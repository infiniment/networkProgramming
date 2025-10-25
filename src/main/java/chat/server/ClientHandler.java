package chat.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ClientHandler
 * -----------------------------------------------------
 * - 클라이언트 1명과의 통신을 담당하는 클래스 (Runnable)
 * - ChatServer에서 클라이언트 접속이 발생하면 스레드풀에 의해 실행됨.
 * - 클라이언트로부터 수신한 메시지를 Hub를 통해 전체에게 브로드캐스트.
 * - 연결 종료 시 정리 및 알림 전송.
 */
class ClientHandler implements Runnable {

    /**
     * 연결된 클라이언트에게 순차적으로 부여할 임시 닉네임 번호.
     * 예: guest-1, guest-2 ...
     */
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final Socket socket; // 현재 클라이언트 소켓
    private final Hub hub;       // 메시지 방송용 중앙 허브

    ClientHandler(Socket socket, Hub hub) {
        this.socket = socket;
        this.hub = hub;
    }

    /**
     * 클라이언트 통신을 담당하는 스레드의 메인 로직.
     * 1️⃣ 입출력 스트림 생성 (UTF-8)
     * 2️⃣ Hub에 출력 스트림 등록
     * 3️⃣ 첫 입력을 닉네임으로 설정
     * 4️⃣ 이후 수신한 모든 메시지를 Hub로 브로드캐스트
     * 5️⃣ '/quit' 입력 또는 연결 종료 시 정리
     */
    @Override
    public void run() {
        // 기본 닉네임 (guest-번호)
        String user = "guest-" + SEQ.getAndIncrement();

        try (
                // 클라이언트 입력(수신) 스트림
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                // 클라이언트 출력(송신) 스트림
                PrintWriter pw = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            // 🔹 허브에 현재 클라이언트의 출력 스트림 등록
            hub.join(pw);

            // 🔹 클라이언트에게 안내 메시지 전송
            pw.println("[SYSTEM] connected. (닉네임 바꾸려면 첫 줄에 원하는 이름 입력)");

            // 🔹 첫 입력 라인을 닉네임으로 사용 (비어 있으면 기본 guest-n 유지)
            String first = br.readLine();
            if (first != null && !first.isBlank()) {
                user = first.trim();
            }

            // 🔹 전체에게 “누가 들어왔는지” 방송
            hub.broadcast("[SYSTEM] " + user + " joined");

            // 🔹 채팅 루프 시작
            String line;
            while ((line = br.readLine()) != null) {
                // '/quit' 명령 시 종료
                if ("/quit".equalsIgnoreCase(line.trim())) break;

                // 받은 메시지를 전체에게 방송
                hub.broadcast(user + ": " + line);
            }

        } catch (IOException e) {
            System.err.println("[WARN] " + user + " 연결 중 오류: " + e.getMessage());
        } finally {
            // 연결 종료 시 소켓 정리
            try { socket.close(); } catch (IOException ignored) {}

            // 🔹 허브에서 퇴장 메시지 방송
            hub.broadcast("[SYSTEM] " + user + " left");
        }
    }
}
