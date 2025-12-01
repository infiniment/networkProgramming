package chat.server;

import chat.util.Constants;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.Set;

public class ChatServer {
    // 현재 접속 중인 모든 클라이언트 핸들러 목록(브로드캐스트용)
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();

    // 닉네임 -> 핸들러 매핑(개인 메시지/세션 조회용)
    private final ConcurrentHashMap<String, ClientHandler> sessions = new ConcurrentHashMap<>();

    // 채팅방 생성/입장/목록 관리를 담당
    private final RoomManager roomManager;

    // 오목 게임 진행/상태 관리를 담당
    private final OmokGameManager gameManager;

    // 베스킨라빈스 31 게임 진행/상태 관리를 담당
    private final BR31GameManager br31GameManager;

    // 서버 리스닝 소켓
    private ServerSocket serverSocket;

    // 접속 사용자(아이디/닉네임 등) 디렉토리 관리
    private final UserDirectory userDirectory = new UserDirectory();

    public ChatServer() {
        // 서버가 사용할 매니저/디렉토리 객체 초기화
        this.roomManager = new RoomManager();
        this.gameManager = new OmokGameManager(this);
        this.br31GameManager = new BR31GameManager(this);
    }

    // 외부에서 사용자 디렉토리에 접근할 수 있게 반환
    public UserDirectory getUserDirectory() { return userDirectory; }

    // 닉네임 기준으로 세션 등록
    public void registerSession(String nick, ClientHandler h) { sessions.put(nick, h); }

    // 닉네임 기준으로 세션 해제
    public void unregisterSession(String nick) { sessions.remove(nick); }

    // 닉네임으로 특정 클라이언트 핸들러 조회
    public ClientHandler getSession(String nick) { return sessions.get(nick); }

    public void start() {
        // 지정 포트로 서버를 열고 클라이언트 접속을 무한 대기/수락
        System.out.println("Chat Server starting on port " + Constants.DEFAULT_PORT + "...");
        try {
            serverSocket = new ServerSocket(Constants.DEFAULT_PORT);
            while (true) {
                Socket socket = serverSocket.accept();

                // 지연 없이 바로 전송되게 함
                socket.setTcpNoDelay(true);

                // 클라이언트당 핸들러 스레드를 만들고 실행
                ClientHandler handler = new ClientHandler(socket, this, roomManager, gameManager, br31GameManager);
                handlers.add(handler);
                handler.start();
                System.out.println("New client connected: " + socket);
            }
        } catch (SocketException se) {
            // 서버 소켓 종료로 인한 예외는 정상 종료로 처리
            if (se.getMessage().contains("Socket closed")) {
                System.out.println("Server listener stopped.");
            } else {
                System.err.println("Server error: " + se.getMessage());
            }
        } catch (IOException e) {
            // 기타 IO 예외 로그 출력
            System.err.println("Server error: " + e.getMessage());
        } finally {
            // 서버 종료 시 방/자원 정리
            roomManager.closeAll();
        }
    }

    public void stop() {
        // 서버 리스너/모든 클라이언트를 종료시키는 정리 루틴
        System.out.println("Chat Server shutting down...");
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}

        // 모든 클라이언트에 종료 알림 후 스레드 중단
        for (ClientHandler h : handlers) {
            try { h.sendMessage("[System] 서버가 종료됩니다."); } catch (Exception ignored) {}
            try { h.interrupt(); } catch (Exception ignored) {}
        }
        roomManager.closeAll();
    }

    public void removeHandler(ClientHandler handler) {
        // 연결 종료된 핸들러를 목록에서 제거하고 로그 남김
        handlers.remove(handler);
        String nick = (handler != null && handler.getNickname() != null) ? handler.getNickname() : "(unknown)";
        System.out.println("Client disconnected: " + nick);
    }

    public void broadcastRoomsList() {
        // 현재 방 목록을 JSON으로 만들어 모든 클라이언트에 전송
        String jsonList = roomManager.listRoomsAsJson();
        String full = Constants.RESPONSE_ROOMS + " " + jsonList;
        for (ClientHandler h : handlers) h.sendMessage(full);
    }

    public void broadcastToAllClients(String command) {
        // 특정 명령이면 방 목록 브로드캐스트로 처리
        if (Constants.CMD_ROOMS_LIST.equals(command)) {
            broadcastRoomsList();
            return;
        }
        // 그 외에는 모든 클라이언트에게 원문 커맨드를 그대로 전송
        for (ClientHandler h : handlers) {
            PrintWriter w = h.outWriter();
            if (w != null) {
                w.println(command);
                w.flush();
            }
        }
    }
}
