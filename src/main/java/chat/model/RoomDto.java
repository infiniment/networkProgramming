package chat.model;

/**
 * 서버와 클라이언트 간에 채팅방 정보를 주고받는 DTO (Data Transfer Object)
 */
public class RoomDto {

    public String name;             // 채팅방 이름
    public int participants;        // 현재 채팅방에 참여 중인 사용자 수
    public int capacity;            // 채팅방의 최대 수용 인원
    public boolean active;          // 활성 여부
    public boolean locked;          // 비밀방 여부

    public RoomDto() {}

    public RoomDto(String name, int participants, int capacity, boolean active, boolean locked) {
        this.name = name;
        this.participants = participants;
        this.capacity = capacity;
        this.active = active;
        this.locked = locked;
    }

    public String toCounter() {
        return participants + "/" + capacity + "명 참여 중";
    }
}