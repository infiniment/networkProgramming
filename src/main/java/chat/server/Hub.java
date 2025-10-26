package chat.server;

import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Hub(허브) 클래스
 * ---------------------------------------
 * - 현재 서버에 접속한 모든 클라이언트의 출력 스트림(PrintWriter)을 관리한다.
 * - ChatServer → ClientHandler가 생성될 때 join()으로 등록한다.
 * - broadcast()를 호출하면 모든 사용자에게 메시지를 전송한다.
 *
 * → 일종의 “중앙 방송 송신기” 역할
 *    나중에 여러 방(Room)을 지원할 때는 RoomManager로 대체 가능.
 */
class Hub {

    /**
     * 현재 연결 중인 클라이언트들의 출력 스트림 목록.
     * CopyOnWriteArraySet:
     *  - 멀티스레드 환경에서도 안전하게 add/remove 가능
     *  - 클라이언트가 많지 않은 채팅 서버에서는 성능 손해 거의 없음
     */
    private final Set<PrintWriter> outs = new CopyOnWriteArraySet<>();

    /**
     * 새 클라이언트가 접속했을 때 출력 스트림을 등록.
     * @param out 클라이언트 소켓의 출력 스트림(PrintWriter)
     */
    void join(PrintWriter out) {
        outs.add(out);
    }

    /**
     * 클라이언트가 연결 종료 시 스트림을 목록에서 제거.
     * @param out 연결 종료된 클라이언트의 PrintWriter
     */
    void leave(PrintWriter out) {
        outs.remove(out);
    }

    /**
     * 서버에 전달된 문자열(line)을 모든 클라이언트에게 방송한다.
     * - 각 PrintWriter에 println() 후 flush()로 즉시 전송.
     * - try/catch 없이 단순 송신만 수행 (예외는 상위에서 처리)
     * @param line 브로드캐스트할 메시지 (한 줄)
     */
    void broadcast(String line) {
        for (PrintWriter out : outs) {
            out.println(line);
            out.flush(); // 버퍼 비우기 (즉시 전송)
        }
    }

    /**
     * 서버 종료 시 모든 클라이언트의 출력 스트림을 닫고 목록을 비운다.
     * - 서버 stop() 또는 종료 훅에서 호출.
     */
    void closeAll() {
        for (PrintWriter out : outs) {
            try { out.close(); } catch (Exception ignored) {}
        }
        outs.clear();
    }
}
