package chat.server;

import chat.shared.model.RoomDto;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RoomManager {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomManager() {
        // 서버 시작 시 DB에 저장된 방들을 메모리로 로딩
        List<Room> loaded = ChatRoomRepository.loadAllRooms();
        for (Room room : loaded) {
            rooms.put(room.getName(), room);
        }
        System.out.println("[RoomManager] DB에서 방 " + rooms.size() + "개 로드 완료");
    }

    // 기존 호환용: CLI에서 쓰는 버전 (비번 없음 → null)
    public boolean createRoom(String name, int capacity, boolean locked) {
        return createRoom(name, capacity, locked, null, "system");
    }


    // 비밀번호까지 받는 새 버전
    public boolean createRoom(String name, int capacity, boolean locked, String password, String ownerName) {
        if (rooms.containsKey(name)) {
            return false;
        }

        String pwd = locked ? password : null;
        Room room = new Room(name, capacity, locked, pwd, ownerName);
        rooms.put(name, room);

        ChatRoomRepository.upsertRoom(room);
        return true;
    }

    // 방 삭제 로직
    public boolean deleteRoom(String roomName) {
        Room removed = rooms.remove(roomName);
        if (removed != null) {
            ChatRoomRepository.deleteRoom(roomName);
            return true;
        }
        return false;
    }


    public Room getRoom(String roomName) {
        return rooms.get(roomName);
    }

    /** ClientHandler 자체로 입장 */
    public Room join(String roomName, ClientHandler ch) {
        Room room = rooms.get(roomName);
        if (room != null && room.addParticipant(ch)) {
            return room;
        }
        return null;
    }

    /** 방이 비어도 유지 */
    public void leave(String roomName, ClientHandler ch) {
        Room room = rooms.get(roomName);
        if (room != null) {
            room.removeParticipant(ch);
        }
    }

    public void broadcast(String roomName, String line) {
        Room room = rooms.get(roomName);
        if (room != null) room.broadcast(line);
    }

    public List<RoomDto> listRooms() {
        return rooms.values().stream()
                .map(Room::toDto)
                .collect(Collectors.toList());
    }

    public String listRoomsAsJson() {
        return chat.util.JsonUtil.roomsToJson(listRooms());
    }

    /** 모든 방에서 해당 클라이언트를 제거 */
    public void removeEverywhere(ClientHandler ch) {
        for (Room room : rooms.values()) {
            room.removeParticipant(ch);
        }
    }

    public void closeAll() {
        rooms.clear(); // ClientHandler 소켓 정리는 개별 핸들러/서버가 담당
    }
}
