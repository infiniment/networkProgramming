package chat.server;

import chat.util.Constants;
import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final ChatServer server;
    private final RoomManager roomManager;
    private PrintWriter out;
    private String nickname;
    private Room currentRoom;

    public ClientHandler(Socket socket, ChatServer server, RoomManager roomManager) {
        this.socket = socket;
        this.server = server;
        this.roomManager = roomManager;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);

            nickname = in.readLine();
            sendMessage("[System] Welcome, " + nickname + "!");

            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("/")) {
                    handleCommand(line);
                } else if (currentRoom != null) {
                    currentRoom.broadcast(nickname + ": " + line);
                }
            }

        } catch (IOException e) {
            System.err.println(nickname + " disconnected unexpectedly. Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ========== 명령어 처리 로직 ==========
    private void handleCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (cmd.equals(Constants.CMD_ROOMS_LIST)) {
            sendRoomListUpdate();
        } else if (cmd.equals(Constants.CMD_ROOM_CREATE)) {
            handleCreateRoom(args);
        } else if (cmd.equals(Constants.CMD_JOIN_ROOM)) {
            handleJoinRoom(args);
        } else if (cmd.equals(Constants.CMD_QUIT)) {
            handleQuit();
        }
        else {
            sendMessage("[System] 알 수 없는 명령어입니다: " + cmd);
        }
    }

    private void handleCreateRoom(String args) {
        String[] parts = args.split(" ");
        if (parts.length < 3) {
            sendMessage("[System] " + Constants.CMD_ROOM_CREATE + " [이름] [정원] [lock|open] 형식으로 입력하세요.");
            return;
        }
        String name = parts[0];
        int capacity;
        boolean locked = parts[2].equalsIgnoreCase("lock");

        try {
            capacity = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendMessage("[System] 정원 값은 숫자여야 합니다.");
            return;
        }

        if (roomManager.createRoom(name, capacity, locked)) {
            sendMessage("[System] 방 생성 성공: " + name);
            server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);
        } else {
            // 방 생성 실패 시 클라이언트에게 알림 메시지 전송
            sendMessage("[System] 방 생성 실패: 이미 존재하는 방입니다.");
        }
    }

    private void handleJoinRoom(String roomName) {
        if (currentRoom != null) {
            handleLeaveRoom(false);
        }

        Room joinedRoom = roomManager.join(roomName, out);
        if (joinedRoom != null) {
            currentRoom = joinedRoom;
            sendMessage("[System] '" + roomName + "' 방에 입장했습니다.");
            currentRoom.broadcast(nickname + "님이 입장했습니다.");
        } else {
            sendMessage("[System] 방 입장에 실패했습니다. 정원 초과이거나 방이 존재하지 않습니다.");
        }
    }

    private void handleLeaveRoom(boolean closeConnection) {
        if (currentRoom == null) return;

        String roomName = currentRoom.getName();
        currentRoom.broadcast(nickname + "님이 퇴장했습니다.");
        roomManager.leave(roomName, out);
        currentRoom = null;

        server.broadcastToAllClients(Constants.CMD_ROOMS_LIST);

        if (closeConnection) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleQuit() {
        handleLeaveRoom(true);
    }

    public void sendMessage(String message) {
        out.println(message);
        out.flush();
    }

    private void sendRoomListUpdate() {
        String jsonList = roomManager.listRoomsAsJson();
        sendMessage(Constants.RESPONSE_ROOMS + " " + jsonList);
    }

    private void cleanup() {
        if (currentRoom != null) {
            handleLeaveRoom(false);
        }
        roomManager.removeEverywhere(out);
        server.removeHandler(this);
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String getNickname() {
        return nickname;
    }
}