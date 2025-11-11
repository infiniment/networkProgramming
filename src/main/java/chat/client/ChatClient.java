package chat.client;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChatClient {
    // 서버랑 연결 소켓
    private Socket socket;

    // 입력 스트림
    private BufferedReader in;

    // 출력 스트림
    private PrintWriter out;

    // 백그라운드에서 메시지를 수신하는 스레드
    private Thread receiveThread;


    // 전송 전용 큐: 모든 send를 비-EDT 백그라운드에서 직렬화
    private ExecutorService outQueue;

    // 클라이언트 실행 상태 플래그 (멀티스레드 안전을 위해 volatile)
    private volatile boolean running = false;

    public void connect(String host, int port) throws IOException {
        // 서버랑 연결
        socket = new Socket(host, port);

        // Nagle 알고리즘 비활성화 → 작은 패킷도 즉시 전송 (채팅은 실시간성이 중요)
        socket.setTcpNoDelay(true);

        // 입출력 스트림 생성 (UTF-8 인코딩 서버랑 동일하게 했음)
        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );

        // autoFlush=true → println() 호출 시 자동으로 flush
        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );

        // 데몬 단일 스레드: 전송을 한 줄씩 순서 보장
        outQueue = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "client-outbound");
            t.setDaemon(true);
            return t;
        });

        // 클라이언트 실행 상태 활성화
        running = true;
        System.out.println("[CLIENT] 서버에 연결되었습니다: " + host + ":" + port);
    }

    /** 비동기 전송: UI/EDT에서 이 메서드만 호출하세요 */
    public void sendAsync(String message) {
        if (!running || socket == null || socket.isClosed()) return;
        if (message == null) return;

        outQueue.submit(() -> {
            try {
                synchronized (out) { // 한 줄 단위 원자성
                    out.println(message);
                    // autoFlush=true 이지만 안전을 위해 확인
                    if (out.checkError()) {
                        System.err.println("[CLIENT] 전송 중 오류(checkError).");
                    }
                }
            } catch (Exception e) {
                System.err.println("[CLIENT] 전송 작업 실패: " + e.getMessage());
            }
        });
    }


    // 서버로 메시지 전송
    public void sendMessage(String message) {
        // 출력 스트림이 유효하고 소켓이 열려있는지 확인
        if (out != null && !socket.isClosed()) {
            // 줄 단위로 메시지 전송
            out.println(message);
        }
    }

    // 서버로부터 메시지를 계속 수신
    public void startReceiving(MessageListener listener) {
        // 별도 스레드에서 수신 루프 실행 (메인 스레드를 블로킹하지 않기 위해)
        receiveThread = new Thread(() -> {
            try {
                String line;
                // 서버로부터 줄 단위로 계속 읽기
                while (running && (line = in.readLine()) != null) {
                    // 리스너를 EDT에서 실행해 UI 스레드 안전 보장
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> listener.onMessageReceived(msg));
                }
//                while (running && (line = in.readLine()) != null) {
//                    // 리스너를 통해 수신한 메시지 전달
//                    listener.onMessageReceived(line);
//                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[CLIENT] 수신 중 오류: " + e.getMessage());
                }
            } finally {
                // EDT에서 종료 콜백
                SwingUtilities.invokeLater(listener::onDisconnected);
//                listener.onDisconnected();
            }
        }, "client-inbound");

        // 데몬 스레드로 설정 → 메인 스레드 종료 시 자동으로 종료
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    // 연결 종료
    public void disconnect() {
        running = false;

        try {
            if (outQueue != null) {
                outQueue.shutdown();
                try { outQueue.awaitTermination(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
                outQueue.shutdownNow();
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[CLIENT] 종료 중 오류: " + e.getMessage());
        }
        System.out.println("[CLIENT] 연결이 종료되었습니다.");
    }
//    public void disconnect() {
//        // 실행 상태 플래그 false → 수신 루프 종료 트리거
//        running = false;
//
//        try {
//            // 입출력 스트림 및 소켓 순차적으로 정리
//            if (in != null) in.close();
//            if (out != null) out.close();
//            if (socket != null && !socket.isClosed()) socket.close();
//        } catch (IOException e) {
//            System.err.println("[CLIENT] 종료 중 오류: " + e.getMessage());
//        }
//
//        System.out.println("[CLIENT] 연결이 종료되었습니다.");
//    }

    // 메시지 수신 리스너 인터페이스
    // GUI나 콘솔이 이 인터페이스를 구현하여 메시지 처리
    public interface MessageListener {
        // 서버에게 메시지 받은 경우 호출
        void onMessageReceived(String message);
        // 서버랑 연결 끊어진 경우 호출
        void onDisconnected();
    }
}