package chat.server;

import chat.util.Constants;
import chat.util.JsonEnvelope;
import chat.util.JsonUtil;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final ChatServer server;
    private final RoomManager roomManager;
    private final UserDirectory users;

    private PrintWriter out;
    private String nickname;
    private Room currentRoom;
    private CommandRouter router;

    public ClientHandler(Socket socket, ChatServer server, RoomManager roomManager) {
        this.socket = socket;
        this.server = server;
        this.roomManager = roomManager;
        this.users = server.getUserDirectory();
    }

    public Room currentRoom() { return currentRoom; }
    public String nickname() { return nickname; }
    public PrintWriter outWriter() { return out; }
    public void sendMessage(String message) {
        out.println(message); out.flush();
    }

    // 닉 변경 시: 세션 레지스트리만 갱신 (UserDirectory는 Router의 /nick 처리에서 관리)
    public void setNickname(String newNick) {
        server.unregisterSession(this.nickname);    // 기존 닉 해제
        this.nickname = newNick;
        server.registerSession(this.nickname, this); // 새 닉 등록
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);

            // 최초 닉 수신 및 등록
            nickname = in.readLine();
            sendMessage("[System] Welcome, " + nickname + "!");
            users.register(nickname, out); // 출력 스트림 등록 (귓속말용)
            server.registerSession(nickname, this); // 세션 등록 (같은 방 검증/다이렉트 접근용)

            // Router: 서버도 함께 주입해야 /to에서 같은 방 검증 가능
            router = new CommandRouter(this, roomManager, users, server);

            // 최초 방 목록 브로드캐스트
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);

            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("/") && handleMediaPacket(line)) {
                    continue; // 처리 끝났으면 다음 루프로
                }
                if (line.startsWith("/")) {
                    // 코어 명령(rooms/join/quit/typing/bomb/게임)은 여기서 처리
                    if (!handleCoreCommands(line)) {
                        // 그 외 확장 명령은 Router로
                        router.route(line);
                    }
                }else if (currentRoom != null) {
                    if (router.isSecretMode()) {
                        String sid = router.currentSecretSid();
                        // 레거시 시크릿
                        currentRoom.broadcast(Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " + line);
                        // JSON 시크릿(선택) : GUI가 준비되면 이걸로 전환 가능
                        broadcastJsonToRoom("secret", line, sid, null, null);
                    } else {
                        // 레거시 일반 채팅
                        currentRoom.broadcast(nickname + ": " + line);
                        // JSON 일반 채팅
                        broadcastJsonToRoom("chat", line, null, null, null);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println(nickname + " disconnected unexpectedly. Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ========== 명령어 처리 로직 ==========
    private boolean handleCoreCommands(String command) {
        String[] parts = command.split(" ", 2);
        String cmd  = parts[0];
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (cmd.equals(Constants.CMD_ROOMS_LIST)) {
            sendRoomListUpdate();
        } else if (cmd.equals(Constants.CMD_ROOM_CREATE)) {
            handleCreateRoom(args);
        } else if (cmd.equals(Constants.CMD_JOIN_ROOM)) {
            handleJoinRoom(args);
        } else if (cmd.equals(Constants.CMD_QUIT)) {
            handleQuit();
        } else if (command.startsWith(Constants.CMD_TYPING_START)) {
            if (currentRoom != null) {
                // 레거시
                currentRoom.broadcast(nickname + ": " + Constants.CMD_TYPING_START);
                // JSON
                broadcastJsonToRoom("typing", null, "start", null, null);
            }
        } else if (command.startsWith(Constants.CMD_TYPING_STOP)) {
            if (currentRoom != null) {
                // 레거시
                currentRoom.broadcast(nickname + ": " + Constants.CMD_TYPING_STOP);
                // JSON
                broadcastJsonToRoom("typing", null, "stop", null, null);
            }
        } else if (cmd.equals(Constants.CMD_BOMB)) {
            handleBomb(args);
        } else if (cmd.equals(Constants.CMD_GOMOKU)) {
            triggerGame("gomoku");
        } else if (cmd.equals(Constants.CMD_31)) {
            triggerGame("br31");
        }else if (cmd.equals(Constants.CMD_EMOJI_LIST)) {
            // 간단 JSON 만들기 (나중에 GUI가 버튼 그릴 때 사용)
            String json = "{\"emoji\":" + JsonUtil.mapToJson(EmojiRegistry.allEmojis())
                    + ",\"sticker\":" + JsonUtil.mapToJson(EmojiRegistry.allStickers()) + "}";
            sendMessage(Constants.EVT_EMOJI_LIST + " " + json);
        } else {
            return false; // 나머지는 Router에서 처리
        }
        return true;
    }


    private void triggerGame(String game) {
        if (currentRoom != null) {
            // 클라: "[GAME] gomoku host=닉" → 게임 패널 열기
            currentRoom.broadcast("[GAME] " + game + " host=" + nickname);
        } else {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
        }
    }



    private void handleCreateRoom(String args) {
        String[] parts = args.split(" ");
        if (parts.length < 3) {
            sendMessage("[System] " + Constants.CMD_ROOM_CREATE + " [이름] [정원] [lock|open] 형식으로 입력하세요.");
            return;
        }
        String name = parts[0];
        int capacity;
        boolean locked = parts[2].equalsIgnoreCase("lock");

        try {
            capacity = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendMessage("[System] 정원 값은 숫자여야 합니다.");
            return;
        }

        if (roomManager.createRoom(name, capacity, locked)) {
            sendMessage("[System] 방 생성 성공: " + name);
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
        } else {
            // 방 생성 실패 시 클라이언트에게 알림 메시지 전송
            sendMessage("[System] 방 생성 실패: 이미 존재하는 방입니다.");
        }
    }

    private void handleBomb(String args) {
        // /bomb [초] [메시지]
        String[] sp = args.split("\\s+", 2);
        int sec = 5;
        String text = "";
        try {
            if (sp.length >= 1) sec = Integer.parseInt(sp[0]);
            if (sp.length >= 2) text = sp[1];
        } catch (Exception ignored) {}
        if (currentRoom != null) {
            currentRoom.broadcast("[BOMB " + sec + "s] " + nickname + ": " + text);
        } else {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
        }
    }

    private void handleJoinRoom(String roomName) {
        if (currentRoom != null) {
            handleLeaveRoom(false);
        }

        Room joinedRoom = roomManager.join(roomName, out);
        if (joinedRoom != null) {
            currentRoom = joinedRoom;
            sendMessage("[System] '" + roomName + "' 방에 입장했습니다.");
            currentRoom.broadcast(nickname + "님이 입장했습니다.");
        } else {
            sendMessage("[System] 방 입장에 실패했습니다. 정원 초과이거나 방이 존재하지 않습니다.");
        }
    }

    private void handleLeaveRoom(boolean closeConnection) {
        if (currentRoom == null) return;

        String roomName = currentRoom.getName();
        currentRoom.broadcast(nickname + "님이 퇴장했습니다.");
        roomManager.leave(roomName, out);
        currentRoom = null;

        server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);

        if (closeConnection) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleQuit() {
        handleLeaveRoom(true);
    }


    private void sendRoomListUpdate() {
        String jsonList = roomManager.listRoomsAsJson();
        sendMessage(Constants.RESPONSE_ROOMS + " " + jsonList);
    }

    private void cleanup() {
        if (currentRoom != null) {
            handleLeaveRoom(false);
        }
        roomManager.removeEverywhere(out);
        server.removeHandler(this);
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String getNickname() {
        return nickname;
    }

    // 이모티콘 보내는 로직
    private boolean handleMediaPacket(String line) {
        if (line.startsWith(Constants.PKG_EMOJI + " ")) {
            String code = line.substring((Constants.PKG_EMOJI + " ").length()).trim();
            String res  = EmojiRegistry.findEmoji(code);
            if (res == null) { sendMessage("[System] 알 수 없는 이모티콘: " + code); return true; }
            if (currentRoom == null) { sendMessage("[System] 방에 입장 중이 아닙니다."); return true; }

            if (router.isSecretMode()) {
                String sid = router.currentSecretSid();
                // 레거시 시크릿
                currentRoom.broadcast(Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " + code);
                // JSON 시크릿 이모티콘 (type을 'emoji.secret'로 두거나 'secret' + status=emoji:...로 둬도 됨)
                broadcastJsonToRoom("emoji.secret", code, /*status*/res, null, null);
            } else {
                // 레거시
                currentRoom.broadcast("[EMOJI] " + nickname + " " + code);
                // JSON (status에 이미지 경로/URL)
                broadcastJsonToRoom("emoji", code, /*status*/res, null, null);
            }
            return true;
        }

        if (line.startsWith(Constants.PKG_STICKER + " ")) {
            String name = line.substring((Constants.PKG_STICKER + " ").length()).trim();
            String res  = EmojiRegistry.findSticker(name);
            if (res == null) { sendMessage("[System] 알 수 없는 스티커: " + name); return true; }
            if (currentRoom == null) { sendMessage("[System] 방에 입장 중이 아닙니다."); return true; }

            if (router.isSecretMode()) {
                String sid = router.currentSecretSid();
                // 레거시 시크릿
                currentRoom.broadcast(Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": [STICKER] " + name);
                // JSON 시크릿 스티커
                broadcastJsonToRoom("sticker.secret", name, /*status*/res, null, null);
            } else {
                // 레거시
                currentRoom.broadcast("[STICKER] " + nickname + " " + name);
                // JSON
                broadcastJsonToRoom("sticker", name, /*status*/res, null, null);
            }
            return true;
        }

        return false;
    }

    private void broadcastJsonToRoom(String type, String text, String status, String to, String ttlMs) {
        if (currentRoom == null) return;
        String payload = JsonEnvelope.build(
                type, nickname, currentRoom.getName(), text, to, status, ttlMs
        );
        currentRoom.broadcast(payload);
    }
}