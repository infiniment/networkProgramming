package chat.server;

import chat.shared.model.RoomDto;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** 서버가 관리하는 개별 채팅방 */
public class Room {
    private static final int HISTORY_LIMIT = 50;   // 방마다 최근 50개만 보관

    private final String name;
    private final int capacity;
    private final boolean locked;
    private final String password;
    private final String ownerName;


    private volatile boolean secretActive = false;
    private volatile String  secretSid    = null;

    public synchronized void startSecret(String sid) {
        this.secretActive = true;
        this.secretSid = sid;
    }
    public synchronized void stopSecret() {
        this.secretActive = false;
        this.secretSid = null;
    }

    public boolean isSecretActive() { return secretActive; }
    public String currentSecretSid() { return secretSid; }

    // PrintWriter 대신 ClientHandler를 직접 보관
    private final List<ClientHandler> participants = new CopyOnWriteArrayList<>();

    private final List<String> history = new CopyOnWriteArrayList<>();

    public Room(String name, int capacity, boolean locked) {
        this(name, capacity, locked, null, "system");
    }

    public Room(String name, int capacity, boolean locked, String password, String ownerName) {
        this.name = name;
        this.capacity = capacity;
        this.locked = locked;
        // locked 체크 안 하고 그냥 저장해도 되지만,
        // 잠금 해제된 방이면 password 안 남도록 null 처리
        this.password = locked ? password : null;
        this.ownerName = ownerName;
    }

    public boolean addParticipant(ClientHandler ch) {
        if (participants.size() >= capacity) return false;
        participants.add(ch);
        return true;
    }

    public void removeParticipant(ClientHandler ch) {
        participants.remove(ch);
    }

    /** 방 전체 브로드캐스트 */
    public void broadcast(String line) {
        for (ClientHandler ch : participants) {
            ch.sendMessage(line);
        }
    }


    // 방에 들어오는 클라이언트에게 줄 히스토리 스냅샷
    public List<String> getHistorySnapshot() {
        return new java.util.ArrayList<>(history);
    }

    public void sendTo(ClientHandler ch, String line) {
        if (ch == null) return;
        PrintWriter w = ch.outWriter();
        if (w != null) {
            w.println(line);
            w.flush();
        }
    }

    /** who() 에서 사용할 참여자 닉네임 목록 */
    public List<String> participantNicknames() {
        return participants.stream()
                .map(ClientHandler::getNickname)
                .collect(Collectors.toList());
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
    public int getCapacity() { return capacity; }
    public boolean isLocked() { return locked; }
    public List<ClientHandler> getParticipants() { return participants; }
    public String getOwnerName() { return ownerName; }

    // 비밀번호 게터
    public String getPassword() { return password; }

    // 나중에 입장 시 비밀번호 검증할 때 사용할 헬퍼 (필요하면 사용)
    public boolean matchPassword(String input) {
        if (!locked) return true;           // 오픈 방은 항상 통과
        if (password == null) return false; // 잠금인데 비밀번호가 없으면 실패
        return password.equals(input);
    }

    public static class MemberRepository {
    }
}
