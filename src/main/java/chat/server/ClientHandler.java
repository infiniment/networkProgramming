package chat.server;

import chat.shared.EmojiRegistry;
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
    private final BR31GameManager br31GameManager;

    private PrintWriter out;
    private String nickname;
    private Room currentRoom;
    private CommandRouter router;

    public ClientHandler(Socket socket, ChatServer server, RoomManager roomManager, OmokGameManager gameManager, BR31GameManager br31GameManager) {  // ✅ 수정
        this.socket = socket;
        this.server = server;
        this.roomManager = roomManager;
        this.users = server.getUserDirectory();
        this.gameManager = gameManager;
        this.br31GameManager = br31GameManager;
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

            // 닉네임 검증: null/공백이면 바로 정리하고 종료
            if (nickname == null || nickname.isBlank()) {
                // 로그만 남기고 정리
                System.out.println("Client connected but no nickname, closing socket: " + socket);
                cleanup(); // nickname이 null이어도 안전하게 동작하도록 cleanup 수정 권장(아래 4번)
                return;
            }
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
                    if (currentRoom != null && currentRoom.isSecretActive()) {
                        String sid = currentRoom.currentSecretSid();
                        currentRoom.broadcast(Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " + line);
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
//    private boolean handleMediaPacket(String line) {
//        boolean roomSecret = currentRoom != null && currentRoom.isSecretActive();
//        String sid = roomSecret ? currentRoom.currentSecretSid() : null;
//
//        if (line.startsWith(Constants.PKG_EMOJI + " ")) {
//            String code = line.substring((Constants.PKG_EMOJI + " ").length()).trim();
//            String res  = EmojiRegistry.findEmoji(code);
//            if (res == null) { sendMessage("[System] 알 수 없는 이모티콘: " + code); return true; }
//            if (currentRoom == null) { sendMessage("[System] 방에 입장 중이 아닙니다."); return true; }
//
//            if (roomSecret) {
//                currentRoom.broadcast(Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " + code);
//                broadcastJsonToRoom("emoji.secret", code, /*status*/res, null, null);
//            } else {
//                currentRoom.broadcast("[EMOJI] " + nickname + " " + code);
//                broadcastJsonToRoom("emoji", code, /*status*/res, null, null);
//            }
//            return true;
//        }
//
//
//        return false;
//    }
    // ✅ 이모티콘 패킷만 처리
    private boolean handleMediaPacket(String line) {
        // 형식: "@PKG_EMOJI :doing:"
        if (!line.startsWith(Constants.PKG_EMOJI + " ")) {
            return false;
        }

        if (currentRoom == null) {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
            return true;
        }

        boolean roomSecret = currentRoom.isSecretActive();
        String sid = roomSecret ? currentRoom.currentSecretSid() : null;

        String code = line.substring((Constants.PKG_EMOJI + " ").length()).trim(); // ":doing:"
        String res  = EmojiRegistry.findEmoji(code);

        if (res == null) {
            sendMessage("[System] 알 수 없는 이모티콘: " + code);
            return true;
        }

        if (roomSecret) {
            // 시크릿 방이면 secret 프로토콜 그대로 이용 (payload에 이모티콘 코드 넣기)
            currentRoom.broadcast(
                    Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " +
                            Constants.PKG_EMOJI + " " + code
            );
            // 선택: JSON 알림 유지하고 싶으면 타입만 맞춰서
            broadcastJsonToRoom("emoji.secret", code, res, null, null);
        } else {
            // 일반 방: 클라이언트에서 "nick: @PKG_EMOJI :doing:" 을 보고 이미지 버블로 렌더
            currentRoom.broadcast(
                    nickname + ": " + Constants.PKG_EMOJI + " " + code
            );
            // 필요하면 JSON도 함께 (UI에서 쓰면 되고, 아니라면 제거 가능)
            broadcastJsonToRoom("emoji", code, res, null, null);
        }

        return true;
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
            currentRoom.broadcast("@game:menu game=" + game + " host=" + nickname);
        } else {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
        }
    }

    private void handleGameJoin(String gameType) {
        System.out.println("[GAME-JOIN] " + nickname + "님이 " + gameType + " 게임 참여");

        String[] parts = gameType.split(" ");
        String game = parts[0];

        // ========== 오목 게임 ==========
        if (game.equals("omok")) {
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
        // ========== BR31 게임 ==========
        else if (game.equals("br31")) {
            String roomId = currentRoom != null ? currentRoom.getName() : "default";

            // 호스트가 인원 설정한 경우: "br31 5"
            if (parts.length > 1) {
                try {
                    int maxPlayers = Integer.parseInt(parts[1]);
                    br31GameManager.handleHostSetup(nickname, roomId, maxPlayers);
                } catch (NumberFormatException e) {
                    sendMessage("[System] 잘못된 인원 수입니다.");
                }
            } else {
                // 일반 참여
                BR31GameManager.JoinResult result = br31GameManager.handlePlayerJoin(nickname, roomId, this);

                switch (result) {
                    case HOST_WAITING:
                        System.out.println("[BR31-JOIN] ⏳ " + nickname + "님이 호스트로 대기");
                        break;

                    case GUEST_JOINED:
                        System.out.println("[BR31-JOIN] 👥 게스트 참여");
                        break;

                    case GAME_STARTED:
                        System.out.println("[BR31-JOIN] 🎮 게임 시작!");
                        break;

                    case ALREADY_IN_GAME:
                        System.out.println("[BR31-JOIN] ⚠️ 이미 게임 중");
                        sendMessage("[System] 이미 게임 중입니다.");
                        break;

                    case ROOM_FULL:
                        System.out.println("[BR31-JOIN] ❌ 방이 꽉 참");
                        sendMessage("[System] 방이 꽉 찼습니다.");
                        break;

                    case ERROR:
                        System.err.println("[BR31-JOIN] ❌ 오류");
                        sendMessage("[System] 게임 참여 중 오류가 발생했습니다.");
                        break;
                }
            }
        }
        // ========== 지원하지 않는 게임 ==========
        else {
            sendMessage("[System] 지원하지 않는 게임입니다: " + game);
        }
    }

    private void handleGameMove(String args) {
        System.out.println("[GAME-MOVE] " + nickname + "님의 이동: " + args);

        String[] parts = args.split(" ");
        if (parts.length < 1) {
            System.err.println("[GAME-MOVE] 형식 오류");
            return;
        }

        try {
            // BR31 게임인지 확인
            BR31GameManager.BR31GameSession br31Session = br31GameManager.getSessionByPlayer(nickname);

            if (br31Session != null) {
                // ========== BR31 게임 이동 처리 ==========
                // args 형식: "3,4,5" (선택한 숫자들)
                String[] numberStrs = args.split(",");
                int[] numbers = new int[numberStrs.length];

                for (int i = 0; i < numberStrs.length; i++) {
                    numbers[i] = Integer.parseInt(numberStrs[i].trim());
                }

                boolean success = br31GameManager.handlePlayerMove(nickname, numbers);

                if (!success) {
                    System.err.println("[BR31-MOVE] " + nickname + "의 이동 실패");
                    sendMessage("[System] ❌ 이동이 실패했습니다.");
                }

            } else {
                // ========== 오목 게임 이동 처리 (기존 로직) ==========
                if (parts.length < 2) {
                    System.err.println("[GAME-MOVE] 오목 형식 오류");
                    return;
                }

                int row = Integer.parseInt(parts[0]);
                int col = Integer.parseInt(parts[1]);

                boolean success = gameManager.recordMoveWithValidation(nickname, row, col);

                if (!success) {
                    System.err.println("[GAME-MOVE] " + nickname + "의 이동 실패");
                    sendMessage("[System] ❌ 이동이 실패했습니다.");
                    return;
                }

                System.out.println("[GAME-MOVE] ✅ 이동 기록됨");
            }

        } catch (NumberFormatException e) {
            System.err.println("[GAME-MOVE] 파싱 오류: " + e.getMessage());
        }
    }

    private void handleGameQuit() {
        System.out.println("[GAME-QUIT] " + nickname + "님이 게임 종료");

        // 오목 게임 종료
        gameManager.handlePlayerDisconnect(nickname);

        // BR31 게임 종료
        br31GameManager.handlePlayerDisconnect(nickname);  // ← 이미 있음!
    }

    private void handleCreateRoom(String args) {
        // 형식 1)  name cap lock|open
        // 형식 2) "name with space" cap lock|open
        String name;
        int capacity;
        boolean locked;

        args = args.trim();
        if (args.startsWith("\"")) {
            // 따옴표 이름
            int end = args.indexOf('"', 1);
            if (end <= 0) { sendMessage("[System] 방 이름 따옴표가 올바르지 않습니다."); return; }
            name = args.substring(1, end);
            String rest = args.substring(end + 1).trim();
            String[] sp = rest.split("\\s+");
            if (sp.length < 2) { sendMessage("[System] 정원/잠금 형식 오류"); return; }
            try { capacity = Integer.parseInt(sp[0]); } catch (Exception e) { sendMessage("[System] 정원은 숫자여야 합니다."); return; }
            locked = sp[1].equalsIgnoreCase("lock");
        } else {
            // 기존 방식
            String[] parts = args.split("\\s+");
            if (parts.length < 3) {
                sendMessage("[System] " + Constants.CMD_ROOM_CREATE + " [이름] [정원] [lock|open]");
                return;
            }
            name = parts[0];
            try { capacity = Integer.parseInt(parts[1]); } catch (Exception e) { sendMessage("[System] 정원은 숫자여야 합니다."); return; }
            locked = parts[2].equalsIgnoreCase("lock");
        }

        if (roomManager.createRoom(name, capacity, locked)) {
            sendMessage("[System] 방 생성 성공: " + name);
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
        } else {
            sendMessage("[System] 방 생성 실패: 이미 존재하는 방입니다.");
        }
//        String[] parts = args.split(" ");
//        if (parts.length < 3) {
//            sendMessage("[System] " + Constants.CMD_ROOM_CREATE + " [이름] [정원] [lock|open] 형식으로 입력하세요.");
//            return;
//        }
//        String name = parts[0];
//        int capacity;
//        boolean locked = parts[2].equalsIgnoreCase("lock");
//
//        try {
//            capacity = Integer.parseInt(parts[1]);
//        } catch (NumberFormatException e) {
//            sendMessage("[System] 정원 값은 숫자여야 합니다.");
//            return;
//        }
//
//        if (roomManager.createRoom(name, capacity, locked)) {
//            sendMessage("[System] 방 생성 성공: " + name);
//            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
//        } else {
//            sendMessage("[System] 방 생성 실패: 이미 존재하는 방입니다.");
//        }
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
            currentRoom.broadcast(Constants.EVT_BOMB + " " + sec + " " + nickname + ": " + text);
        } else {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
        }
    }

    private void handleJoinRoom(String roomName) {
        // 양끝 따옴표/공백 제거
        String rn = roomName.trim().replaceAll("^\"|\"$", "");

        if (currentRoom != null) {
            handleLeaveRoom(false);
        }

        Room joinedRoom = roomManager.join(rn, this);
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
        roomManager.leave(roomName, this);
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
            br31GameManager.handlePlayerDisconnect(nickname);
        }
        roomManager.removeEverywhere(this);
        server.removeHandler(this);
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String getNickname() {
        return nickname;
    }

}