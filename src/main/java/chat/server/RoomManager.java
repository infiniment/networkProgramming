package chat.server;

import chat.model.RoomDto;
import chat.util.JsonUtil;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RoomManager - **[최종]**
 * - 방이 비어도 자동으로 삭제되지 않도록 수정 완료.
 * - 기본 방 생성 로직 제거 완료.
 */
public class RoomManager {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomManager() {
        // 서버 시작 시 기본 채팅방 생성 로직 제거 완료
    }

    public boolean createRoom(String name, int capacity, boolean locked) {
        if (rooms.containsKey(name)) {
            return false;
        }
        Room newRoom = new Room(name, capacity, locked);
        rooms.put(name, newRoom);
        return true;
    }

    public Room join(String roomName, PrintWriter out) {
        Room room = rooms.get(roomName);
        if (room != null && room.getParticipantCount() < room.getCapacity()) {
            room.addParticipant(out);
            return room;
        }
        return null;
    }

    /** 방이 비어도 삭제하지 않고 유지하도록 수정 완료 */
    public void leave(String roomName, PrintWriter out) {
        Room room = rooms.get(roomName);
        if(room != null) {
            room.removeParticipant(out);
        }
    }

    public void broadcast(String roomName, String line) {
        Room room = rooms.get(roomName);
        if (room == null) return;
        room.broadcast(line);
    }

    public List<RoomDto> listRooms() {
        return rooms.values().stream()
                .map(Room::toDto)
                .collect(Collectors.toList());
    }

    public String listRoomsAsJson() {
        List<RoomDto> dtos = listRooms();
        return chat.util.JsonUtil.roomsToJson(dtos);
    }

    /** 방이 비어도 삭제하지 않고 유지하도록 수정 완료 */
    public void removeEverywhere(PrintWriter out) {
        for (Room room : rooms.values()) {
            room.removeParticipant(out);
        }
    }

    public void closeAll() {
        for (Room room : rooms.values()) {
            for (PrintWriter out : room.getParticipants()) {
                try { out.close(); } catch (Exception ignored) {}
            }
            room.getParticipants().clear();
        }
        rooms.clear();
    }
}