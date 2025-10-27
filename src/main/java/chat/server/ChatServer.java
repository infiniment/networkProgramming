package chat.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatServer
 * -------------------------------------------------------------
 * 채팅 서버의 핵심 로직 클래스.
 *
 * 주요 역할
 * - 지정된 포트에 바인딩하여 클라이언트 접속을 기다린다.
 * - 클라이언트가 연결되면, 각 연결을 ClientHandler 스레드로 처리한다.
 * - RoomManager를 통해 방(Room) 단위로 메시지를 관리/브로드캐스트한다.
 * - UserDirectory를 통해 현재 접속 중인 사용자 목록(닉네임 ↔ 스트림)을 관리한다.
 *
 * 동작 구조
 * [Main] → ChatServer.start()
 *   → ServerSocket.accept() 로 접속 수락
 *   → ClientHandler 스레드 생성 및 실행 (Thread Pool 사용)
 *   → 각 ClientHandler가 RoomManager, UserDirectory를 활용해
 *     메시지를 방송하거나 특정 사용자에게 전송
 *
 * 구성 요소
 * - RoomManager  : 방 단위 메시지 관리 및 브로드캐스트
 * - UserDirectory : 닉네임 ↔ 출력 스트림 관리 (귓속말, 상태 표시 등)
 * - ExecutorService(pool) : 클라이언트별 스레드 관리
 *
 * 엔트리포인트
 * - 설정 파일(application.yml)에서 포트를 읽어 ChatServer를 실행하는 부분은
 *   chat.Main 클래스에서 담당한다.
 */

public class ChatServer {
    // 리스닝 포트
    private final int port;

    // 접속자 핸들러를 실행할 스레드풀(필요할 때 스레드 생성/재사용)
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final RoomManager rooms = new RoomManager();
    private final UserDirectory userDirectory = new UserDirectory();

    // 서버 소켓(accept 대기)
    private ServerSocket serverSocket;

    // 외부에서 stop() 호출 시 루프를 끊기 위한 플래그
    private volatile boolean running = false;

    public ChatServer(int port) {
        this.port = port;
    }

    /**
     * 서버를 시작한다.
     * - ServerSocket을 생성해 포트에 바인딩
     * - 무한 루프에서 accept()로 접속을 받고, 클라이언트마다 ClientHandler를 실행
     * - 종료 훅에서 stop()이 호출되도록 등록
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);                 // 재시작 시 TIME_WAIT 포트 재사용
        serverSocket.bind(new InetSocketAddress(port));
        running = true;

        System.out.println("[SERVER] listening on " + port);

        // JVM 종료 시 리소스 정리
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { stop(); } catch (IOException ignored) {}
        }));

        // 접속 수락 루프
        while (running && !serverSocket.isClosed()) {
            try {
                Socket sock = serverSocket.accept();        // 블로킹 대기
                sock.setTcpNoDelay(true);                   // Nagle 끄기(지연 최소화)
                System.out.println("[SERVER] accepted " + sock.getRemoteSocketAddress());

                // 클라이언트 1명 담당 핸들러를 스레드풀에서 실행
                pool.submit(new ClientHandler(sock, rooms, userDirectory));

            } catch (IOException e) {
                // stop()에서 close()가 호출되면 accept()가 IOException으로 깨어남
                if (running) {
                    System.err.println("[SERVER] accept error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 서버를 정지한다.
     * - 더 이상 새 연결을 받지 않도록 플래그 내리고 ServerSocket close
     * - 허브에 등록된 출력 스트림 정리
     * - 스레드풀 강제 종료
     */
    public void stop() throws IOException {
        System.out.println("[SERVER] stopping...");
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }

        rooms.closeAll();          // 모든 클라이언트 출력 종료
        pool.shutdownNow();      // 대기/실행 중인 작업 중단 시도
        System.out.println("[SERVER] stopped.");
    }
}
