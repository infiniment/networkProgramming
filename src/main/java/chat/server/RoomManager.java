package chat.server;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RoomManager
 * ------------------------------------------------------------
 * - 서버의 모든 "채팅방(Room)"을 관리하는 클래스
 * - 각 방마다 클라이언트들의 출력 스트림(PrintWriter)을 보관
 * - broadcast()를 통해 같은 방의 모든 사용자에게 메시지를 전송
 *
 * 스레드 안전(Thread-safe)하게 동작하도록
 * ConcurrentHashMap + CopyOnWriteArraySet을 사용한다.
 */
public class RoomManager {
    /**
     * 전체 방 목록을 저장하는 맵
     *  Key   : 방 이름(String)
     *  Value : 해당 방에 참여 중인 클라이언트들의 출력 스트림 집합(Set<PrintWriter>)
     *
     * ConcurrentHashMap : 여러 스레드(ClientHandler)에서 동시에 join/leave 해도 안전
     * CopyOnWriteArraySet : 각 방의 참여자 목록을 안전하게 관리 (읽기 위주라 적합)
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PrintWriter>> rooms = new ConcurrentHashMap<>();

    /**
     * 클라이언트가 특정 방에 들어올 때 호출된다.
     * - 해당 방이 없으면 새로 만들고, 있으면 기존 방에 추가.
     * @param room 입장할 방 이름
     * @param out 클라이언의 출력 스트림
     */
    public void join(String room, PrintWriter out) {
        // computeIfAbsent : 방이 없으면 새 Set을 만들어 등록 후 반환
        rooms.computeIfAbsent(room, r -> new CopyOnWriteArrayList<>()).add(out);
    }

    /**
     * 클라이언트가 방에서 나갈 때 호출된다.
     * - 해당 방의 사용자 목록에서 PrintWriter 제거.
     * - 만약 방이 비면(참가자 0명) 방 자체를 제거한다.
     *
     * @param room 나갈 방 이름
     * @param out  클라이언트의 출력 스트림(PrintWriter)
     */
    public void leave(String room, PrintWriter out) {
        var set = rooms.get(room);
        if(set != null) {
            set.remove(out); // 방 사용자 목록에서 제거
            if(set.isEmpty())
                rooms.remove(room); // 방이 비면 삭제
        }
    }

    /**
     * 같은 방(room)에 있는 모든 클라이언트에게 메시지를 전송한다.
     * - 방이 없으면 빈 집합으로 대체되어 아무 일도 일어나지 않음.
     * - 각 클라이언트의 PrintWriter로 println() + flush() 호출.
     *
     * @param room 메시지를 보낼 방 이름
     * @param line 전송할 문자열 (한 줄)
     */
    public void broadcast(String room, String line) {
        var set = rooms.get(room);
        if (set == null) return;
        for (var out : set) {
            out.println(line);
            out.flush();
        }
    }


    /**
     * 현재 존재하는 모든 방 이름 목록을 반환한다.
     * - unmodifiableSet으로 감싸서 외부에서 수정할 수 없게 함.
     *
     * @return 현재 서버에 존재하는 방 이름 집합 (읽기 전용)
     */
    public Set<String> listRooms() {
        return Collections.unmodifiableSet(rooms.keySet());
    }

    /** 어떤 방에 있든 이 writer를 전부 제거 (비정상 종료/예외 대비) */
    public void removeEverywhere(PrintWriter out) {
        for (var entry : rooms.entrySet()) {
            var set = entry.getValue();
            set.remove(out);
            if (set.isEmpty()) rooms.remove(entry.getKey());
        }
    }

    /** 서버 종료 시 모든 클라이언트 스트림 정리 */
    public void closeAll() {
        for (var set : rooms.values()) {
            for (var out : set) {
                try { out.close(); } catch (Exception ignored) {}
            }
            set.clear();
        }
        rooms.clear();
    }

}
