package chat.server;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserDirectory
 * --------------------------------------------
 * - 현재 서버에 접속 중인 모든 사용자(닉네임 ↔ 출력 스트림)를 관리하는 클래스.
 * - 서버 전체에서 "특정 사용자에게만 메시지를 보내는 기능(귓속말 등)"이 필요할 때 사용한다.
 * - 예: /w 명령, 타이핑 중 표시, 비밀채팅, 상태 알림 등
 *
 * 역할 요약
 *   - register()   : 클라이언트가 닉네임을 정하면 등록 (닉네임 → PrintWriter)
 *   - unregister() : 클라이언트가 접속 종료 시 제거
 *   - get()        : 닉네임으로 특정 사용자의 PrintWriter를 찾아냄
 *   - exists()     : 해당 닉네임이 이미 존재하는지 확인
 *
 * Thread-safe
 *   - ConcurrentHashMap 사용 → 여러 클라이언트 스레드가 동시에 등록/삭제해도 안전.
 */
public class UserDirectory {
    /**
     * 현재 접속 중인 모든 사용자 정보를 저장하는 맵.
     * Key   : 사용자의 닉네임 (고유 식별자)
     * Value : 해당 사용자의 출력 스트림(PrintWriter)
     *          서버가 특정 사용자에게 메시지를 전송할 때 사용.
     */
    private final ConcurrentHashMap<String, PrintWriter> users = new ConcurrentHashMap<>();

    /**
     * 사용자 등록 (닉네임/Writer 검증 + 중복 방지)
     * @return true=등록 성공, false=실패(잘못된 인자/중복)
     */
    public boolean register(String nickname, PrintWriter out) {
        if (nickname == null || nickname.isBlank() || out == null) return false;
        // 이미 존재하면 덮어쓰지 않음
        return users.putIfAbsent(nickname, out) == null;
    }

    /** 사용자 제거 (닉네임 null/공백 무시) */
    public void unregister(String nickname) {
        if (nickname == null || nickname.isBlank()) return;
        users.remove(nickname);
    }

    /** 닉네임으로 Writer 조회 (닉네임 null/공백이면 null) */
    public PrintWriter get(String nickname) {
        if (nickname == null || nickname.isBlank()) return null;
        return users.get(nickname);
    }

    /** 닉네임 존재 여부 */
    public boolean exists(String nickname) {
        if (nickname == null || nickname.isBlank()) return false;
        return users.containsKey(nickname);
    }

    /** 닉네임 사용 가능 여부(= 미등록) */
    public boolean isAvailable(String nickname) {
        if (nickname == null || nickname.isBlank()) return false;
        return !users.containsKey(nickname);
    }

    /**
     * Writer → 닉네임 역탐색
     * (참가자 목록 만들 때 Room의 PrintWriter를 닉으로 바꿀 때 사용)
     */
    public String nicknameOf(PrintWriter out) {
        if (out == null) return null;
        for (var e : users.entrySet()) {
            if (e.getValue() == out) return e.getKey();
        }
        return null;
    }

    /** 현재 전체 닉네임 스냅샷 */
    public List<String> allNicknames() {
        return new ArrayList<>(users.keySet());
    }

    /** 현재 접속자 수 */
    public int size() {
        return users.size();
    }
}
