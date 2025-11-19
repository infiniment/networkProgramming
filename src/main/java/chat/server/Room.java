//package chat.server;
//
//import chat.shared.model.RoomDto; // ← 네가 쓰는 실제 DTO 패키지로 맞춰줘
//import java.io.PrintWriter;
//import java.util.List;
//import java.util.concurrent.CopyOnWriteArrayList;
//
///**
// * 서버가 관리하는 개별 채팅방 객체입니다. (정원, 잠금 여부, 참여자 스트림 보관)
// */
//public class Room {
//    private final String name;
//    private final int capacity;
//    private final boolean locked;
//    private final List<PrintWriter> participants = new CopyOnWriteArrayList<>();
//
//    public Room(String name, int capacity, boolean locked) {
//        this.name = name;
//        this.capacity = capacity;
//        this.locked = locked;
//    }
//
//    public void addParticipant(PrintWriter out) {
//        if (participants.size() < capacity) {
//            participants.add(out);
//        }
//    }
//
//    public void removeParticipant(PrintWriter out) {
//        participants.remove(out);
//    }
//
//    public void broadcast(String line) {
//        for (PrintWriter out : participants) {
//            out.println(line);
//            out.flush();
//        }
//    }
//
//    public RoomDto toDto() {
//        return new RoomDto(
//                this.name,
//                this.participants.size(),
//                this.capacity,
//                this.participants.size() > 0, // 활성 기준: 참가자 수 > 0
//                this.locked
//        );
//    }
//
//    public String getName() { return name; }
//    public int getParticipantCount() { return participants.size(); }
//    public List<PrintWriter> getParticipants() { return participants; }
//    public int getCapacity() { return capacity; } // RoomManager join 체크용
//}

package chat.server;

import chat.shared.model.RoomDto;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** 서버가 관리하는 개별 채팅방 */
public class Room {
    private final String name;
    private final int capacity;
    private final boolean locked;

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

    public Room(String name, int capacity, boolean locked) {
        this.name = name;
        this.capacity = capacity;
        this.locked = locked;
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
//    public void broadcast(String line) {
//        for (ClientHandler ch : participants) {
//            ch.sendMessage(line);
//        }
//    }
    public void broadcast(String line) {
        String prefixedLine = "[" + this.name + "] " + line;

        // ✅ 로그 추가
        System.out.println("[SERVER-BROADCAST] room=" + this.name + " → " + prefixedLine);

        for (ClientHandler ch : participants) {
            PrintWriter w = ch.outWriter();
            if (w != null) {
                w.println(prefixedLine);
                w.flush();
            }
        }
    }

    public void sendTo(ClientHandler ch, String line) {
        if (ch == null) return;

        // ✅ room 정보 추가
        String prefixedLine = "[" + this.name + "] " + line;

        PrintWriter w = ch.outWriter();
        if (w != null) {
            w.println(prefixedLine);
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
}
