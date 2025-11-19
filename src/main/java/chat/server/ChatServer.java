package chat.server;

import chat.util.Constants;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.Set;

public class ChatServer {
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ClientHandler> sessions = new ConcurrentHashMap<>();
    private final RoomManager roomManager;
    private final OmokGameManager gameManager;
    private final BR31GameManager br31GameManager;  // ✅ 추가!
    private ServerSocket serverSocket;
    private final UserDirectory userDirectory = new UserDirectory();

    public ChatServer() {
        this.roomManager = new RoomManager();
        this.gameManager = new OmokGameManager(this);
        this.br31GameManager = new BR31GameManager(this);  // ✅ 초기화!
    }

    public UserDirectory getUserDirectory() { return userDirectory; }
    public OmokGameManager getGameManager() { return gameManager; }
    public BR31GameManager getBR31GameManager() { return br31GameManager; }  // ✅ 게터 추가!

    public void registerSession(String nick, ClientHandler h) { sessions.put(nick, h); }
    public void unregisterSession(String nick) { sessions.remove(nick); }
    public ClientHandler getSession(String nick) { return sessions.get(nick); }

    public void start() {
        System.out.println("Chat Server starting on port " + Constants.DEFAULT_PORT + "...");
        try {
            serverSocket = new ServerSocket(Constants.DEFAULT_PORT);
            while (true) {
                Socket socket = serverSocket.accept();

                // 작은 패킷도 즉시 전송
                socket.setTcpNoDelay(true);

                // ✅ BR31GameManager도 전달!
                ClientHandler handler = new ClientHandler(socket, this, roomManager, gameManager, br31GameManager);
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

    public void stop() {
        System.out.println("Chat Server shutting down...");
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}

        for (ClientHandler h : handlers) {
            try { h.sendMessage("[System] 서버가 종료됩니다."); } catch (Exception ignored) {}
            try { h.interrupt(); } catch (Exception ignored) {}
        }
        roomManager.closeAll();
    }

    public void removeHandler(ClientHandler handler) {
        handlers.remove(handler);
        String nick = (handler != null && handler.getNickname() != null) ? handler.getNickname() : "(unknown)";
        System.out.println("Client disconnected: " + nick);
    }

    public void broadcastRoomsList() {
        String jsonList = roomManager.listRoomsAsJson();
        String full = Constants.RESPONSE_ROOMS + " " + jsonList;
        for (ClientHandler h : handlers) h.sendMessage(full);
    }

    public void broadcastToAllClients(String command) {
        if (Constants.CMD_ROOMS_LIST.equals(command)) {
            broadcastRoomsList();
            return;
        }
        for (ClientHandler h : handlers) {
            PrintWriter w = h.outWriter();
            if (w != null) {
                w.println(command);
                w.flush();
            }
        }
    }
}