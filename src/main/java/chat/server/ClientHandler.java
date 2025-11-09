package chat.server;

import chat.util.Constants;
import chat.util.JsonEnvelope;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final ChatServer server;
    private final RoomManager roomManager;
    private final UserDirectory users;
    private final OmokGameManager gameManager;

    private PrintWriter out;
    private String nickname;
    private Room currentRoom;
    private CommandRouter router;

    public ClientHandler(Socket socket, ChatServer server, RoomManager roomManager, OmokGameManager gameManager) {  // ✅ 수정
        this.socket = socket;
        this.server = server;
        this.roomManager = roomManager;
        this.users = server.getUserDirectory();
        this.gameManager = gameManager;
    }

    public Room currentRoom() { return currentRoom; }
    public String nickname() { return nickname; }
    public PrintWriter outWriter() { return out; }
    public void sendMessage(String message) {
        out.println(message); out.flush();
    }

    public void setNickname(String newNick) {
        server.unregisterSession(this.nickname);
        this.nickname = newNick;
        server.registerSession(this.nickname, this);
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);

            nickname = in.readLine();
            sendMessage("[System] Welcome, " + nickname + "!");
            users.register(nickname, out);
            server.registerSession(nickname, this);

            router = new CommandRouter(this, roomManager, users, server);

            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);

            String line;
            while ((line = in.readLine()) != null) {
                // 입력 로그
                System.out.printf("[SERVER-LOG] [RECV] (%s): %s%n", nickname, line);

                // 이모티콘/스티커 처리 우선
                if (handleMediaPacket(line)) {
                    continue;
                }

                if (line.startsWith("/")) {
                    if (!handleCoreCommands(line)) {
                        router.route(line);
                    }
                } else if (currentRoom != null) {
                    if (router.isSecretMode()) {
                        String sid = router.currentSecretSid();
                        currentRoom.broadcast(
                                Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " + line
                        );
                    } else {

                        if (line.startsWith("@game:")) {
                            // 🎮 게임 관련 메시지는 prefix 제거
                            System.out.printf("[SERVER-LOG] [GAME-BROADCAST] from=%s msg=%s%n", nickname, line);
                            currentRoom.broadcast(line);
                        } else {
                            // 💬 일반 메시지는 기존처럼 prefix 포함
                            System.out.printf("[SERVER-LOG] [CHAT-BROADCAST] from=%s msg=%s%n", nickname, line);
                            currentRoom.broadcast(nickname + ": " + line);
                        }

                    }
                }
            }

        } catch (IOException e) {
            System.err.println(nickname + " disconnected unexpectedly. Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

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
            if (currentRoom != null) currentRoom.broadcast(nickname + ": " + Constants.CMD_TYPING_START);
        } else if (command.startsWith(Constants.CMD_TYPING_STOP)) {
            if (currentRoom != null) currentRoom.broadcast(nickname + ": " + Constants.CMD_TYPING_STOP);
        } else if (cmd.equals(Constants.CMD_BOMB)) {
            handleBomb(args);
        } else if (cmd.equals(Constants.CMD_GOMOKU)) {
            triggerGame("gomoku");
        } else if (cmd.equals(Constants.CMD_31)) {
            triggerGame("br31");
        } else if (cmd.equals(Constants.CMD_GAME_JOIN)) {
            handleGameJoin(args);
        } else if (cmd.equals(Constants.CMD_GAME_MOVE)) {
            handleGameMove(args);
        } else if (cmd.equals(Constants.CMD_GAME_QUIT)) {
            handleGameQuit();
        } else {
            return false;
        }
        return true;
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

    private void triggerGame(String game) {
        if (currentRoom != null) {
            currentRoom.broadcast("[GAME] " + game + " host=" + nickname);
        } else {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
        }
    }

    private void handleGameJoin(String gameType) {
        System.out.println("[GAME-JOIN] " + nickname + "님이 " + gameType + " 게임 참여");

        if (!gameType.equals("omok")) {
            sendMessage("[System] 현재 오목(omok) 게임만 지원합니다.");
            return;
        }

        OmokGameManager.GameJoinResult result = gameManager.handlePlayerJoin(nickname, this);

        switch (result) {
            case WAITING:
                System.out.println("[GAME-JOIN] ⏳ " + nickname + "님이 호스트로 대기");
                sendMessage(Constants.RESPONSE_GAME_WAITING);
                break;

            case GAME_STARTED:
                System.out.println("[GAME-JOIN] 🎮 게임 매칭 완료!");
                break;

            case HOST_NOT_FOUND:
                System.err.println("[GAME-JOIN] ❌ 호스트 없음");
                sendMessage("[System] 상대방을 찾을 수 없습니다.");
                break;

            case ALREADY_IN_GAME:
                System.out.println("[GAME-JOIN] ⚠️ 이미 게임 중");
                sendMessage("[System] 이미 게임 중입니다.");
                break;

            case ERROR:
                System.err.println("[GAME-JOIN] ❌ 오류");
                sendMessage("[System] 게임 참여 중 오류가 발생했습니다.");
                break;
        }
    }

    private void handleGameMove(String args) {
        System.out.println("[GAME-MOVE] " + nickname + "님의 이동: " + args);

        String[] parts = args.split(" ");
        if (parts.length < 2) {
            System.err.println("[GAME-MOVE] 형식 오류");
            return;
        }

        try {
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);

            boolean success = gameManager.recordMoveWithValidation(nickname, row, col);

            if (!success) {
                System.err.println("[GAME-MOVE] " + nickname + "의 이동 실패");
                sendMessage("[System] ❌ 이동이 실패했습니다.");
                return;
            }

            System.out.println("[GAME-MOVE] ✅ 이동 기록됨");

        } catch (NumberFormatException e) {
            System.err.println("[GAME-MOVE] 파싱 오류");
        }
    }

    private void handleGameQuit() {
        System.out.println("[GAME-QUIT] " + nickname + "님이 게임 종료");
        gameManager.handlePlayerDisconnect(nickname);
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
            sendMessage("[System] 방 생성 실패: 이미 존재하는 방입니다.");
        }
    }

    private void handleBomb(String args) {
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
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
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
        if (nickname != null) {
            gameManager.handlePlayerDisconnect(nickname);
        }
        roomManager.removeEverywhere(out);
        server.removeHandler(this);
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String getNickname() {
        return nickname;
    }

}