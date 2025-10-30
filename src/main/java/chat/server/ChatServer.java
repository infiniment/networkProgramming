package chat.server;

import chat.util.Constants;
import chat.util.JsonEnvelope;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.Set;

public class ChatServer {
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ClientHandler> sessions = new ConcurrentHashMap<>();
    private final RoomManager roomManager;
    private ServerSocket serverSocket;
    private final UserDirectory userDirectory = new UserDirectory();

    public ChatServer() {
        this.roomManager = new RoomManager();
    }

    public UserDirectory getUserDirectory() { return userDirectory; } // 게터

    public void registerSession(String nick, ClientHandler h) { sessions.put(nick, h); }
    public void unregisterSession(String nick) { sessions.remove(nick); }
    public ClientHandler getSession(String nick) { return sessions.get(nick); }

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

            // 2-1) 레거시: "@rooms <배열JSON>"
            String legacy = Constants.RESPONSE_ROOMS + " " + jsonList;

            // 2-2) 신규 JSON: type="rooms", text에 배열JSON 싣기
            String payload = JsonEnvelope.build(
                    "rooms", "server", null, jsonList, null, null, null
            );

            for (ClientHandler h : handlers) {
                h.sendMessage(legacy);
                h.sendMessage(payload);
            }
        } else {
            for (ClientHandler h : handlers) h.sendMessage(command);
        }
    }
}