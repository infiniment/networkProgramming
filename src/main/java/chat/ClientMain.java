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

            // 서버에 닉네임 전송
            client.sendMessage(nickname);

            client.startReceiving(new ChatClient.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    prettyPrint(message); // JSON 메시지를 보기 좋게 출력
                }

                @Override
                public void onDisconnected() {
                    System.out.println("[CLIENT] 서버와의 연결이 끊어졌습니다.");
                }
            });

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
    private static void prettyPrint(String line) {
        String room = "lobby";
        String json = line;
        if (line.startsWith("[") && line.contains("]")) {
            int idx = line.indexOf(']');
            room = line.substring(1, idx).trim();
            json = line.substring(idx + 1).trim();
        }
        String type = find(json, "\"type\":\"", "\"");
        String from = find(json, "\"from\":\"", "\"");
        String text = find(json, "\"text\":\"", "\"");
        System.out.printf("(%s) type=%s from=%s text=%s%n", room, type, from, text);
    }
    private static String find(String s, String a, String end) {
        int i = s.indexOf(a);
        if (i < 0) return null;
        int j = s.indexOf(end, i + a.length());
        if (j < 0) return null;
        return s.substring(i + a.length(), j);
    }
}