package chat;

import chat.client.ui.LoginFrame;
import chat.server.ChatServer;
import chat.util.Constants;

import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Main {

    public static void main(String[] args) {
        final String host = Constants.DEFAULT_HOST;
        final int port = Constants.DEFAULT_PORT;

        boolean alreadyRunning = isPortOpen(host, port, 250);

        if (!alreadyRunning) {
            ChatServer server = new ChatServer();

            Thread serverThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    System.err.println("[Main] 서버 시작 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "ChatServer-Thread");

            serverThread.setDaemon(true);
            serverThread.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.stop();
                } catch (Throwable ignored) {}
            }));

            System.out.println("[Main] 서버를 백그라운드 스레드로 시작했습니다.");
        } else {
            System.out.println("[Main] 서버가 이미 실행 중입니다. (@" + host + ":" + port + ")");
        }

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