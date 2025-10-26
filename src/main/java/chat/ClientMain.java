package chat;

import chat.client.ChatClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;

// 콘솔에서 채팅 클라이언트를 실행하는 메인
public class ClientMain {

    public static void main(String[] args) {
        // 기본 설정
        String host = "localhost";
        int port = 5959;

        System.out.println("=== 채팅 클라이언트 ===");
        System.out.print("닉네임을 입력하세요: ");

        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            // 닉네임 입력
            String nickname = consoleReader.readLine();

            if (nickname == null || nickname.isBlank()) {
                nickname = "guest";
            }

            // 클라이언트 생성 및 연결
            ChatClient client = new ChatClient();
            client.connect(host, port);

            // 서버에 닉네임 전송 (첫 번째 메시지)
            client.sendMessage(nickname);

            // 서버로부터 메시지 수신 시작
            client.startReceiving(new ChatClient.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    // 서버에서 받은 메시지를 콘솔에 출력
                    System.out.println(message);
                }

                @Override
                public void onDisconnected() {
                    System.out.println("[CLIENT] 서버와의 연결이 끊어졌습니다.");
                }
            });

            // 사용자 입력 루프
            System.out.println("채팅을 시작합니다. 종료하려면 '/quit'를 입력하세요.");
            String input;
            while ((input = consoleReader.readLine()) != null) {
                // /quit 입력 시 종료
                if ("/quit".equalsIgnoreCase(input.trim())) {
                    client.sendMessage("/quit");
                    break;
                }

                // 메시지 전송
                client.sendMessage(input);
            }

            // 연결 종료
            client.disconnect();

        } catch (Exception e) {
            System.err.println("클라이언트 실행 중 오류 발생:");
            e.printStackTrace();
        }
    }
}