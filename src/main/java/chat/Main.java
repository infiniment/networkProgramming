package chat;

import chat.server.ChatServer;
import chat.util.YamlConfig;

public class Main {
    public static void main(String[] args) {
        try {
            // application.yml에서 포트 읽기 (없으면 기본값 5959)
            Integer ymlPort = YamlConfig.integer("app.server.port");
            int port = (ymlPort != null) ? ymlPort : 5959;

            // ChatServer 인스턴스 생성 및 실행
            ChatServer server = new ChatServer(port);
            server.start();

        } catch (Exception e) {
            System.err.println("서버 실행 중 오류 발생:");
            e.printStackTrace();
        }
    }
}
