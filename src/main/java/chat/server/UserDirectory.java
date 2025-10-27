package chat.server;

import java.io.PrintWriter;
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
     * 새 사용자를 디렉토리에 등록한다.
     * - 보통 ClientHandler에서 닉네임이 확정된 직후 호출된다.
     *
     * @param nickname 사용자의 닉네임
     * @param out      해당 사용자의 출력 스트림 (서버→클라이언트 전송용)
     */
    public void register(String nickname, PrintWriter out) { users.put(nickname, out); }

    /**
     * 사용자가 서버에서 나갔을 때 디렉토리에서 제거한다.
     * - ClientHandler의 finally 블록에서 호출됨.
     *
     * @param nickname 퇴장한 사용자의 닉네임
     */
    public void unregister(String nickname) { users.remove(nickname); }

    /**
     * 닉네임으로 해당 사용자의 출력 스트림(PrintWriter)을 가져온다.
     * - 귓속말(/w) 같은 기능에서 특정 사용자에게 메시지를 보낼 때 사용됨.
     *
     * @param nickname 찾을 닉네임
     * @return 해당 사용자의 PrintWriter (없으면 null)
     */
    public PrintWriter get(String nickname) { return users.get(nickname); }

    /**
     * 특정 닉네임이 현재 접속 중인지 확인한다.
     *
     * @param nickname 확인할 닉네임
     * @return true = 이미 접속 중, false = 존재하지 않음
     */
    public boolean exists(String nickname) { return users.containsKey(nickname); }
}
