package chat.client;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    // 서버랑 연결 소켓
    private Socket socket;

    // 입력 스트림
    private BufferedReader in;

    // 출력 스트림
    private PrintWriter out;

    // 백그라운드에서 메시지를 수신하는 스레드
    private Thread receiveThread;

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

        // 클라이언트 실행 상태 활성화
        running = true;
        System.out.println("[CLIENT] 서버에 연결되었습니다: " + host + ":" + port);
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
                    // 리스너를 통해 수신한 메시지 전달
                    listener.onMessageReceived(line);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[CLIENT] 수신 중 오류: " + e.getMessage());
                }
            } finally {
                listener.onDisconnected();
            }
        });

        // 데몬 스레드로 설정 → 메인 스레드 종료 시 자동으로 종료
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    // 연결 종료
    public void disconnect() {
        // 실행 상태 플래그 false → 수신 루프 종료 트리거
        running = false;

        try {
            // 입출력 스트림 및 소켓 순차적으로 정리
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[CLIENT] 종료 중 오류: " + e.getMessage());
        }

        System.out.println("[CLIENT] 연결이 종료되었습니다.");
    }

    // 메시지 수신 리스너 인터페이스
    // GUI나 콘솔이 이 인터페이스를 구현하여 메시지 처리
    public interface MessageListener {
        // 서버에게 메시지 받은 경우 호출
        void onMessageReceived(String message);
        // 서버랑 연결 끊어진 경우 호출
        void onDisconnected();
    }
}