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

    private long memberId;
    private PrintWriter out;
    private String nickname;
    private Room currentRoom;
    private CommandRouter router;

    public ClientHandler(Socket socket, ChatServer server, RoomManager roomManager, OmokGameManager gameManager, BR31GameManager br31GameManager) {
        // 클라이언트 연결에 필요한 객체들을 주입받아 초기화
        this.socket = socket;
        this.server = server;
        this.roomManager = roomManager;
        this.users = server.getUserDirectory();
        this.gameManager = gameManager;
        this.br31GameManager = br31GameManager;
    }

    // 현재 참여 중인 방 객체를 반환
    public Room currentRoom() { return currentRoom; }

    // 현재 핸들러의 닉네임을 반환
    public String nickname() { return nickname; }

    // 현재 클라이언트로 출력할 PrintWriter를 반환
    public PrintWriter outWriter() { return out; }

    // 현재 클라이언트에게 메시지 1줄 전송
    public void sendMessage(String message) {
        out.println(message); out.flush();
    }

    @Override
    public void run() {
        // 클라이언트로부터 입력을 읽고 명령/채팅을 처리하는 메인 루프
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);

            nickname = in.readLine();

            // 닉네임이 없거나 공백이면 바로 연결 종료
            if (nickname == null || nickname.isBlank()) {
                System.out.println("Client connected but no nickname, closing socket: " + socket);
                cleanup();
                return;
            }

            // 닉네임을 기준으로 멤버를 조회/생성하고 memberId를 확보
            memberId = MemberRepository.findOrCreateByName(nickname);
            System.out.println("[LOGIN] memberId=" + memberId + ", name=" + nickname);

            sendMessage("[System] Welcome, " + nickname + "!");
            users.register(nickname, out);
            server.registerSession(nickname, this);

            router = new CommandRouter(this, roomManager, users, server);

            // 접속 직후 모든 클라이언트에게 방 목록 갱신을 요청
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);

            String line;
            while ((line = in.readLine()) != null) {
                // 수신된 원문 로그 출력
                System.out.printf("[SERVER-LOG] [RECV] (%s): %s%n", nickname, line);

                // 이모티콘 같은 미디어 패킷이면 우선 처리 후 다음 라인으로 넘어감
                if (handleMediaPacket(line)) {
                    continue;
                }

                if (line.startsWith("/")) {
                    // 서버 핵심 명령어면 여기서 처리하고 아니면 라우터로 전달
                    if (!handleCoreCommands(line)) {
                        router.route(line);
                    }
                } else if (currentRoom != null) {
                    if (currentRoom.isSecretActive()) {
                        // 시크릿 모드에서는 DB 저장 없이 참여자에게만 시크릿 이벤트로 전송
                        String sid = currentRoom.currentSecretSid();
                        String payload = Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " + line;
                        currentRoom.broadcast(payload);

                    } else {
                        if (line.startsWith("@game:")) {
                            // 게임 패킷은 그대로 방에 브로드캐스트하고 DB에 저장하지 않음
                            System.out.printf("[SERVER-LOG] [GAME-BROADCAST] from=%s msg=%s%n", nickname, line);
                            currentRoom.broadcast(line);

                        } else {
                            // 일반 채팅은 "닉: 내용" 형태로 브로드캐스트하고 DB에도 저장
                            System.out.printf("[SERVER-LOG] [CHAT-BROADCAST] from=%s msg=%s%n", nickname, line);
                            String payload = nickname + ": " + line;
                            currentRoom.broadcast(payload);

                            ChatMessageRepository.saveMessage(
                                    currentRoom.getName(),
                                    nickname,
                                    line,
                                    false
                            );
                        }
                    }
                }
            }

        } catch (IOException e) {
            // 소켓 끊김/입출력 오류 등 연결 문제를 처리
            System.err.println(nickname + " disconnected unexpectedly. Error: " + e.getMessage());
        } finally {
            // 연결 종료 시 등록된 상태/자원을 정리
            cleanup();
        }
    }

    private boolean handleCoreCommands(String command) {
        // /로 시작하는 서버 내장 명령(방/게임/타이핑/종료 등)을 분기 처리
        String[] parts = command.split(" ", 2);
        String cmd  = parts[0];
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (cmd.equals(Constants.CMD_ROOMS_LIST)) {
            sendRoomListUpdate();
        } else if (cmd.equals(Constants.CMD_ROOM_CREATE)) {
            handleCreateRoom(args);
        } else if (cmd.equals(Constants.CMD_ROOM_DELETE)) {
            handleDeleteRoom(args);
        } else if (cmd.equals(Constants.CMD_JOIN_ROOM)) {
            handleJoinRoom(args);
        } else if (cmd.equals(Constants.CMD_LEAVE_ROOM)) {
            handleLeaveRoom(false);
            sendMessage("[System] 방에서 나왔습니다.");
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
        }
        else if (cmd.equals(Constants.CMD_QUIT)) {
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

    // 이모티콘 패킷("@PKG_EMOJI ...")만 처리하고 처리했으면 true를 반환
    private boolean handleMediaPacket(String line) {
        if (!line.startsWith(Constants.PKG_EMOJI + " ")) {
            return false;
        }

        // 방에 없으면 처리 불가라 안내 메시지 전송
        if (currentRoom == null) {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
            return true;
        }

        boolean roomSecret = currentRoom.isSecretActive();
        String sid = roomSecret ? currentRoom.currentSecretSid() : null;

        // 이모티콘 코드(":doing:" 같은)를 추출하고 레지스트리에서 유효성 확인
        String code = line.substring((Constants.PKG_EMOJI + " ").length()).trim();
        String res  = EmojiRegistry.findEmoji(code);

        // 등록되지 않은 코드면 실패 안내
        if (res == null) {
            sendMessage("[System] 알 수 없는 이모티콘: " + code);
            return true;
        }

        // 시크릿 방이면 시크릿 이벤트로, 일반 방이면 일반 텍스트 패킷으로 브로드캐스트
        if (roomSecret) {
            currentRoom.broadcast(
                    Constants.EVT_SECRET_MSG + " " + sid + " " + nickname + ": " +
                            Constants.PKG_EMOJI + " " + code
            );
        } else {
            currentRoom.broadcast(
                    nickname + ": " + Constants.PKG_EMOJI + " " + code
            );
        }

        return true;
    }

    private void broadcastJsonToRoom(String type, String text, String status, String to, String ttlMs) {
        // JSON 포맷 이벤트를 만들어 현재 방 전체에 브로드캐스트
        if (currentRoom == null) return;
        String payload = JsonEnvelope.build(
                type, nickname, currentRoom.getName(), text, to, status, ttlMs
        );
        currentRoom.broadcast(payload);
    }

    private void triggerGame(String game) {
        // 현재 방에 게임 메뉴 오픈 이벤트를 브로드캐스트
        if (currentRoom != null) {
            currentRoom.broadcast("@game:menu game=" + game + " host=" + nickname);
        } else {
            sendMessage("[System] 방에 입장 중이 아닙니다.");
        }
    }

    private void handleGameJoin(String gameType) {
        // 게임 참여 요청을 게임 종류에 따라 오목/BR31로 분기 처리
        System.out.println("[GAME-JOIN] " + nickname + "님이 " + gameType + " 게임 참여");

        String[] parts = gameType.split(" ");
        String game = parts[0];

        if (game.equals("omok")) {
            OmokGameManager.GameJoinResult result = gameManager.handlePlayerJoin(nickname, this);

            switch (result) {
                case WAITING:
                    sendMessage(Constants.RESPONSE_GAME_WAITING);
                    break;
                case GAME_STARTED:
                    break;
                case HOST_NOT_FOUND:
                    sendMessage("[System] 상대방을 찾을 수 없습니다.");
                    break;
                case ALREADY_IN_GAME:
                    sendMessage("[System] 이미 게임 중입니다.");
                    break;
                case ERROR:
                    sendMessage("[System] 게임 참여 중 오류가 발생했습니다.");
                    break;
            }
        } else if (game.equals("br31")) {
            // BR31은 방 이름을 세션 키로 사용
            String roomId = currentRoom != null ? currentRoom.getName() : "default";

            // 호스트 설정("br31 5" 같은 인원 설정)이면 설정 처리
            if (parts.length > 1) {
                try {
                    int maxPlayers = Integer.parseInt(parts[1]);
                    br31GameManager.handleHostSetup(nickname, roomId, maxPlayers);
                } catch (NumberFormatException e) {
                    sendMessage("[System] 잘못된 인원 수입니다.");
                }
            } else {
                // 일반 참여 요청 처리
                BR31GameManager.JoinResult result = br31GameManager.handlePlayerJoin(nickname, roomId, this);

                switch (result) {
                    case HOST_WAITING:
                        break;
                    case GUEST_JOINED:
                        break;
                    case GAME_STARTED:
                        break;
                    case ALREADY_IN_GAME:
                        sendMessage("[System] 이미 게임 중입니다.");
                        break;
                    case ROOM_FULL:
                        sendMessage("[System] 방이 꽉 찼습니다.");
                        break;
                    case ERROR:
                        sendMessage("[System] 게임 참여 중 오류가 발생했습니다.");
                        break;
                }
            }
        } else {
            // 지원하지 않는 게임 타입이면 안내
            sendMessage("[System] 지원하지 않는 게임입니다: " + game);
        }
    }

    private void handleGameMove(String args) {
        // 게임 이동(오목 좌표 또는 BR31 숫자 선택)을 현재 세션 종류에 맞게 처리
        System.out.println("[GAME-MOVE] " + nickname + "님의 이동: " + args);

        String[] parts = args.split(" ");
        if (parts.length < 1) {
            return;
        }

        try {
            // 먼저 BR31 세션이 있는지 확인해서 BR31이면 BR31 로직으로 처리
            BR31GameManager.BR31GameSession br31Session = br31GameManager.getSessionByPlayer(nickname);

            if (br31Session != null) {
                String[] numberStrs = args.split(",");
                int[] numbers = new int[numberStrs.length];

                for (int i = 0; i < numberStrs.length; i++) {
                    numbers[i] = Integer.parseInt(numberStrs[i].trim());
                }

                boolean success = br31GameManager.handlePlayerMove(nickname, numbers);
                if (!success) {
                    sendMessage("[System] 이동이 실패했습니다.");
                }

            } else {
                // BR31이 아니면 오목 좌표(row col)로 처리
                if (parts.length < 2) {
                    return;
                }

                int row = Integer.parseInt(parts[0]);
                int col = Integer.parseInt(parts[1]);

                boolean success = gameManager.recordMoveWithValidation(nickname, row, col);
                if (!success) {
                    sendMessage("[System] 이동이 실패했습니다.");
                    return;
                }
            }

        } catch (NumberFormatException e) {
            // 숫자 파싱 실패 시 무시하거나 로그만 남김
        }
    }

    private void handleGameQuit() {
        // 현재 플레이어의 게임 세션을 종료 처리(오목/BR31 모두)
        gameManager.handlePlayerDisconnect(nickname);
        br31GameManager.handlePlayerDisconnect(nickname);
    }

    private void handleCreateRoom(String args) {
        // 방 생성 명령의 인자(name/cap/lock/password)를 파싱한 뒤 RoomManager로 생성 요청
        String name;
        int capacity;
        boolean locked;
        String password = null;

        args = args.trim();

        if (args.startsWith("\"")) {
            int end = args.indexOf('"', 1);
            if (end <= 0) {
                sendMessage("[System] 방 이름 따옴표가 올바르지 않습니다.");
                return;
            }
            name = args.substring(1, end);
            String rest = args.substring(end + 1).trim();
            String[] sp = rest.split("\\s+");

            if (sp.length < 2) {
                sendMessage("[System] 정원/잠금 형식 오류");
                return;
            }

            try {
                capacity = Integer.parseInt(sp[0]);
            } catch (Exception e) {
                sendMessage("[System] 정원은 숫자여야 합니다.");
                return;
            }

            locked = sp[1].equalsIgnoreCase("lock");

            if (locked && sp.length >= 3) {
                password = sp[2];
            }

        } else {
            String[] parts = args.split("\\s+");
            if (parts.length < 3) {
                sendMessage("[System] " + Constants.CMD_ROOM_CREATE + " [이름] [정원] [lock|open] [비밀번호(선택)]");
                return;
            }

            name = parts[0];

            try {
                capacity = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                sendMessage("[System] 정원은 숫자여야 합니다.");
                return;
            }

            locked = parts[2].equalsIgnoreCase("lock");

            if (locked && parts.length >= 4) {
                password = parts[3];
            }
        }

        if (locked && (password == null || password.isEmpty())) {
            sendMessage("[System] 잠금 방은 비밀번호가 필요합니다.");
            return;
        }

        if (roomManager.createRoom(name, capacity, locked, password, nickname)) {
            sendMessage("[System] 방 생성 성공: " + name);
            if (locked) {
                sendMessage("[System] 잠금 방 비밀번호: " + password);
            }
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
        } else {
            sendMessage("[System] 방 생성 실패: 이미 존재하는 방입니다.");
        }
    }

    private void handleBomb(String args) {
        // 폭탄 이벤트(초/텍스트)를 파싱해 방에 브로드캐스트
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

    private void handleJoinRoom(String args) {
        // 방 입장 인자(roomName/password)를 파싱하고 잠금방이면 비밀번호 검증 후 입장 처리
        args = (args == null) ? "" : args.trim();
        String roomName;
        String password = null;

        if (args.startsWith("\"")) {
            int end = args.indexOf('"', 1);
            if (end <= 0) {
                sendMessage("[System] 방 이름 따옴표가 올바르지 않습니다.");
                return;
            }

            roomName = args.substring(1, end).trim();
            String rest = args.substring(end + 1).trim();

            if (!rest.isEmpty()) {
                password = rest;
            }

        } else {
            String[] parts = args.split("\\s+", 2);
            roomName = parts.length > 0 ? parts[0].trim() : "";
            password = parts.length > 1 ? parts[1].trim() : null;
        }

        if (roomName.isEmpty()) {
            sendMessage("[System] 방 이름을 입력하세요.");
            return;
        }

        try {
            if (currentRoom != null) {
                handleLeaveRoom(false);
            }

            Room targetRoom = roomManager.getRoom(roomName);
            if (targetRoom == null) {
                sendMessage("[System] 존재하지 않는 방입니다.");
                return;
            }

            if (targetRoom.isLocked()) {
                if (password == null || password.isEmpty()) {
                    sendMessage("[System] 이 방은 비밀번호가 필요합니다.");
                    return;
                }

                if (!targetRoom.matchPassword(password)) {
                    sendMessage("[System] 비밀번호가 틀렸습니다.");
                    return;
                }
            }

            Room joinedRoom = roomManager.join(roomName, this);
            if (joinedRoom != null) {
                currentRoom = joinedRoom;

                // 방 입장 직후 최근 채팅을 DB에서 로드해 클라이언트에 먼저 전송
                ChatMessageRepository.loadRecentMessages(currentRoom.getName(), 50)
                        .forEach(this::sendMessage);

                sendMessage("[System] " + nickname + "님이 " + roomName + "에 입장하였습니다.");
                currentRoom.broadcast(nickname + "님이 입장했습니다.");
                server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
            } else {
                sendMessage("[System] 방 입장에 실패했습니다.");
            }
        } catch (Exception e) {
            sendMessage("[System] 방 입장 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDeleteRoom(String args) {
        // 방 주인인지와 방 인원 수를 확인한 뒤 방을 삭제 요청
        String roomName = args.trim().replaceAll("^\"|\"$", "");
        if (roomName.isEmpty()) {
            sendMessage("[System] " + Constants.CMD_ROOM_DELETE + " [방이름] 형식으로 사용하세요.");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendMessage("[System] 존재하지 않는 방입니다: " + roomName);
            return;
        }

        if (!nickname.equals(room.getOwnerName())) {
            sendMessage("[System] 이 방을 삭제할 권한이 없습니다. (방 주인: " + room.getOwnerName() + ")");
            return;
        }

        if (room.getParticipantCount() > 0) {
            sendMessage("[System] 방 안에 사람이 있을 때는 삭제할 수 없습니다.");
            return;
        }

        boolean ok = roomManager.deleteRoom(roomName);
        if (ok) {
            sendMessage("[System] 방을 삭제했습니다: " + roomName);
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
        } else {
            sendMessage("[System] 방 삭제에 실패했습니다.");
        }
    }

    private void handleLeaveRoom(boolean closeConnection) {
        // 현재 방에서 퇴장 처리하고 필요하면 소켓까지 닫음
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
        // 방에서 나가고 연결까지 종료하는 처리
        handleLeaveRoom(true);
    }

    private void sendRoomListUpdate() {
        // 현재 방 목록 JSON을 현재 클라이언트에게만 전송
        String jsonList = roomManager.listRoomsAsJson();
        sendMessage(Constants.RESPONSE_ROOMS + " " + jsonList);
    }

    private void cleanup() {
        // 연결 종료 시 방/게임/세션 등록을 모두 정리하고 소켓을 닫음
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
        // 현재 닉네임을 외부에서 조회할 수 있게 반환
        return nickname;
    }
}
