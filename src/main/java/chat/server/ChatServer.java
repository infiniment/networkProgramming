package chat.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 채팅 서버의 핵심 로직 클래스.
 * - 포트로 바인딩하여 클라이언트 접속을 받는다.
 * - 접속되면 ClientHandler를 스레드풀에 제출한다.
 * - Hub를 통해 모든 클라이언트에 메시지를 브로드캐스트한다.
 *
 * 엔트리포인트(설정 읽기 포함)는 chat.Main에서 담당한다.
 */
public class ChatServer {
    // 리스닝 포트
    private final int port;

    // 접속자 핸들러를 실행할 스레드풀(필요할 때 스레드 생성/재사용)
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // 연결된 클라이언트에게 메시지를 방송하는 허브(룸 매니저로 확장 예정)
    private final Hub hub = new Hub();

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
                pool.submit(new ClientHandler(sock, hub));

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

        hub.closeAll();          // 모든 클라이언트 출력 종료
        pool.shutdownNow();      // 대기/실행 중인 작업 중단 시도
        System.out.println("[SERVER] stopped.");
    }
}
