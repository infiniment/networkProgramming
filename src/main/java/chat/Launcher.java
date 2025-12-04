package chat;

import chat.ui.main.LoginFrame;
import chat.server.ChatServer;

import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Launcher {

    public static void main(String[] args) {
        final String host = "localhost";
        final int port = 5959;

        // 서버가 이미 떠있는지 확인
        boolean alreadyRunning = isPortOpen(host, port, 250);

        if (!alreadyRunning) {
            // 서버를 백그라운드 스레드로 기동
            ChatServer server = new ChatServer();

            Thread serverThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    System.err.println("[Launcher] 서버 시작 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "ChatServer-Thread");
            serverThread.setDaemon(true);
            serverThread.start();
        }

        // 곧바로 GUI 실행 (EDT)
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}