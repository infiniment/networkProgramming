package chat.server;

import chat.model.RoomDto;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 서버가 관리하는 개별 채팅방 객체입니다. (정원, 잠금 여부, 참여자 스트림 보관)
 */
public class Room {
    private final String name;
    private final int capacity;
    private final boolean locked;
    private final List<PrintWriter> participants = new CopyOnWriteArrayList<>();

    public Room(String name, int capacity, boolean locked) {
        this.name = name;
        this.capacity = capacity;
        this.locked = locked;
    }

    public void addParticipant(PrintWriter out) {
        if (participants.size() < capacity) {
            participants.add(out);
        }
    }

    public void removeParticipant(PrintWriter out) {
        participants.remove(out);
    }

    public void broadcast(String line) {
        for (PrintWriter out : participants) {
            out.println(line);
            out.flush();
        }
    }

    public RoomDto toDto() {
        return new RoomDto(
                this.name,
                this.participants.size(),
                this.capacity,
                this.participants.size() > 0, // 활성 기준: 참가자 수 > 0
                this.locked
        );
    }

    public String getName() { return name; }
    public int getParticipantCount() { return participants.size(); }
    public List<PrintWriter> getParticipants() { return participants; }
    public int getCapacity() { return capacity; } // RoomManager join 체크용
}