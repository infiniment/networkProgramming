package chat;

import chat.client.ui.LoginFrame;
import chat.server.ChatServer;
import chat.util.Constants; // Constants 사용을 위해 추가가 필요할 수 있습니다.
import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Launcher {

    public static void main(String[] args) {
        // Constants를 사용하도록 수정하거나, 기존 hardcoding을 유지합니다.
        // 현재 Launcher는 5959를 사용하므로, 그대로 두겠습니다.
        final String host = "localhost";
        final int port = 5959;

        // 1) 서버가 이미 떠있는지 확인 (동일 PC에서 여러 번 켤 때 중복 방지)
        boolean alreadyRunning = isPortOpen(host, port, 250);

        if (!alreadyRunning) {
            // 2) 서버를 백그라운드 스레드로 기동
            // ChatServer server = new ChatServer(port); <-- 에러 라인 수정
            ChatServer server = new ChatServer();

            Thread serverThread = new Thread(() -> {
                try {
                    server.start(); // 서버 루프(블로킹) - 스레드에서 수행
                } catch (Exception e) {
                    System.err.println("[Launcher] 서버 시작 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "ChatServer-Thread");
            serverThread.setDaemon(true); // 앱 종료 시 자동 정리
            serverThread.start();
        }

        // 3) 곧바로 GUI 실행 (EDT)
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    /** host:port가 열려있는지 간단 체크 (서버 중복 실행 방지용) */
    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true; // 연결 성공 → 이미 서버 동작 중
        } catch (IOException e) {
            return false; // 연결 실패 → 서버 없음
        }
    }
}