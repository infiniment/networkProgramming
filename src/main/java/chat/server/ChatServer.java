package chat.server;

import chat.util.Constants;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.Set;

public class ChatServer {
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    private final RoomManager roomManager;
    private ServerSocket serverSocket;

    public ChatServer() {
        this.roomManager = new RoomManager();
    }

    public void start() {
        System.out.println("Chat Server starting on port " + Constants.DEFAULT_PORT + "...");
        try {
            serverSocket = new ServerSocket(Constants.DEFAULT_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this, roomManager);
                handlers.add(handler);
                handler.start();
                System.out.println("New client connected: " + socket);
            }
        } catch (SocketException se) {
            if (se.getMessage().contains("Socket closed")) {
                System.out.println("Server listener stopped.");
            } else {
                System.err.println("Server error: " + se.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            roomManager.closeAll();
        }
    }

    public void stop() { // Launcher/Main에서 호출되는 안전 종료 메서드
        System.out.println("Chat Server shutting down...");
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        roomManager.closeAll();
    }

    public void removeHandler(ClientHandler handler) {
        handlers.remove(handler);
        System.out.println("Client disconnected: " + handler.getNickname());
    }

    public void broadcastToAllClients(String command) {
        if (command.equals(Constants.CMD_ROOMS_LIST)) {
            String jsonList = roomManager.listRoomsAsJson();
            String fullCommand = Constants.RESPONSE_ROOMS + " " + jsonList;

            for (ClientHandler handler : handlers) {
                handler.sendMessage(fullCommand);
            }
        } else {
            for (ClientHandler handler : handlers) {
                handler.sendMessage(command);
            }
        }
    }
}