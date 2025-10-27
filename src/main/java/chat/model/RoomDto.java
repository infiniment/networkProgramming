package chat.model;

public class RoomDto {
    public String name; // 채팅방 이름
    public int participants; // 현재 채팅방에 참여 중인 사용자 수
    public int capacity; // 채팅방의 최대 수용 인원
    public boolean active; // 활성 여부 (true : 대화 중, false : 비활성 또는 인원 0명)
    public boolean locked; // 비밀방 여루(true : 잠금 상태, 비밀번호 필요)

    // 기본 생성자(직렬화 JSON 파싱용)
    public RoomDto() {}

    /**
     * 모든 필드를 직접 지정하는 생성자
     *
     * @param name 방 이름
     * @param participants 현재 참여 인원 수
     * @param capacity 최대 인원 수
     * @param active 활성 여부
     * @param locked 잠금 여부
     */
    public RoomDto(String name, int participants, int capacity, boolean active, boolean locked) {
        this.name = name;
        this.participants = participants;
        this.capacity = capacity;
        this.active = active;
        this.locked = locked;
    }


    /**
     * 인원 수를 보기 좋은 문자열 형태로 반환.
     * 예: "3/10명 참여 중"
     *
     * @return 인원 현황 문자열
     */
    public String toCounter() {
        return participants + "/" + capacity + "명 참여 중";
    }
}
