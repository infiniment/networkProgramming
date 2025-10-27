package chat.server;

import chat.util.JsonEnvelope;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ClientHandler (multi-room, single-connection)
 * - 한 연결로 여러 방에 동시 가입/수신 가능
 * - activeRoom을 기본 송신 대상으로 사용
 * - 명령:
 *   /rooms
 *   /join <room>
 *   /leave <room>
 *   /switch <room>
 *   /to <room> <msg>
 *   /quit
 */
class ClientHandler implements Runnable {

    /**
     * 연결된 클라이언트에게 순차적으로 부여할 임시 닉네임 번호.
     * 예: guest-1, guest-2 ...
     */
    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final String DEFAULT_ROOM = "lobby";

    private final Socket socket; // 현재 클라이언트 소켓
    private final RoomManager rooms;       // 메시지 방송용 중앙 허브
    private final UserDirectory userDirectory;

    // 다중 방 기능을 만들기 위해 클라이언트가 여러 방에 join 가능하도록 Set사용(단일 스레드 사용 전제여서 HashSet)
    private final Set<String> joinedRooms = new HashSet<>();
    private String activeRoom = DEFAULT_ROOM;

    // 닉네임
    private String user = "guest-" + SEQ.getAndIncrement();

    ClientHandler(Socket socket, RoomManager rooms, UserDirectory userDirectory) {
        this.socket = socket;
        this.rooms = rooms;
        this.userDirectory = userDirectory;
    }

    /**
     * 클라이언트 통신을 담당하는 스레드의 메인 로직.
     * 입출력 스트림 생성 (UTF-8)
     * Hub에 출력 스트림 등록
     * 첫 입력을 닉네임으로 설정
     * 이후 수신한 모든 메시지를 Hub로 브로드캐스트
     * '/quit' 입력 또는 연결 종료 시 정리
     */
    @Override
    public void run() {
        BufferedReader br = null;
        PrintWriter pw = null;
        try {
            // 클라이언트 입력(수신) 스트림
            br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 클라이언트 출력(송신) 스트림
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);


            // 기본 방 가입
            joinRoom(DEFAULT_ROOM, pw, false);
            // 첫 줄이 닉네임이면 확정
            pw.println(tag(DEFAULT_ROOM, JsonEnvelope.build("system", null, DEFAULT_ROOM,
                    "connected. enter nickname on first line (optional)", null, null, null)));


            // 첫 입력 라인을 닉네임으로 사용 (비어 있으면 기본 guest-n 유지)
            String first = br.readLine();
            if (first != null && !first.isBlank() && !first.startsWith("/")) {
                user = first.trim();
            }
            // 닉네임 확정되었으므로 디렉토리에 등록
            userDirectory.register(user, pw);

            // 방에 입장 방송
            rooms.broadcast(activeRoom, tag(activeRoom,
                    JsonEnvelope.build("system", user, activeRoom, user+" joined", null, null, null)));

            // 채팅 루프 시작
            String line;
            while ((line = br.readLine()) != null) {
                String msg = line.trim();
                if (msg.isEmpty()) continue;

                // 종료
                if ("/quit".equalsIgnoreCase(msg)) {
                    break;
                }

                // 방/가입 상태 조회
                if ("/rooms".equalsIgnoreCase(msg)) {
                    pw.println(tag(activeRoom, JsonEnvelope.build(
                            "system", user, activeRoom,
                            "rooms=" + rooms.listRooms(), null, null, null
                    )));
                    pw.println(tag(activeRoom, JsonEnvelope.build(
                            "system", user, activeRoom,
                            "joined=" + joinedRooms + ", active=" + activeRoom, null, null, null
                    )));
                    continue;
                }

                // 방 입장
                if (msg.startsWith("/join ")) {
                    String r = msg.substring(6).trim();
                    if (r.isEmpty()) {
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "사용법: /join <room>", null, null, null
                        )));
                        continue;
                    }
                    if (!joinedRooms.contains(r)) {
                        joinRoom(r, pw, /*announce*/ true); // 내부에서 system: "<user> joined" 방송
                    } else {
                        pw.println(tag(r, JsonEnvelope.build(
                                "system", user, r,
                                "already joined: " + r, null, null, null
                        )));
                    }
                    // 편의상 join하면 active도 그 방으로 전환
                    activeRoom = r;
                    pw.println(tag(activeRoom, JsonEnvelope.build(
                            "system", user, activeRoom,
                            "active=" + activeRoom, null, null, null
                    )));
                    continue;
                }

                // 방 탈퇴
                if (msg.startsWith("/leave ")) {
                    String r = msg.substring(7).trim();
                    if (r.isEmpty()) {
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "사용법: /leave <room>", null, null, null
                        )));
                        continue;
                    }
                    if (!joinedRooms.contains(r)) {
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "not joined: " + r, null, null, null
                        )));
                        continue;
                    }
                    // 퇴장 방송 후 leave
                    rooms.broadcast(r, tag(r, JsonEnvelope.build(
                            "system", user, r,
                            user + " left", null, null, null
                    )));
                    rooms.leave(r, pw);
                    joinedRooms.remove(r);

                    // activeRoom 조정
                    if (r.equals(activeRoom)) {
                        if (joinedRooms.isEmpty()) {
                            // 아무 방도 없으면 로비 재조인
                            joinRoom(DEFAULT_ROOM, pw, /*announce*/ true);
                            activeRoom = DEFAULT_ROOM;
                        } else {
                            activeRoom = joinedRooms.iterator().next();
                        }
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "active=" + activeRoom, null, null, null
                        )));
                    }
                    continue;
                }

                // 송신 대상 방 전환
                if (msg.startsWith("/switch ")) {
                    String r = msg.substring(8).trim();
                    if (joinedRooms.contains(r)) {
                        activeRoom = r;
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "active=" + activeRoom, null, null, null
                        )));
                    } else {
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "not joined: " + r, null, null, null
                        )));
                    }
                    continue;
                }

                // 특정 방으로 즉시 송신
                if (msg.startsWith("/to ")) {
                    int sp = msg.indexOf(' ', 4);
                    if (sp <= 4) {
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "usage: /to <room> <message>", null, null, null
                        )));
                        continue;
                    }
                    String r = msg.substring(4, sp).trim();
                    String body = msg.substring(sp + 1);
                    if (!joinedRooms.contains(r)) {
                        pw.println(tag(activeRoom, JsonEnvelope.build(
                                "system", user, activeRoom,
                                "not joined: " + r, null, null, null
                        )));
                        continue;
                    }
                    rooms.broadcast(r, tag(r, JsonEnvelope.build(
                            "chat", user, r,
                            body, null, null, null
                    )));
                    continue;
                }

                // 일반 메시지 → activeRoom으로 전송
                if (!joinedRooms.contains(activeRoom)) {
                    // 이론적 가드(정상흐름에선 발생 X)
                    joinRoom(activeRoom, pw, /*announce*/ true);
                }

                // 일반 채팅 → JSON envelope로 브로드캐스트
                rooms.broadcast(activeRoom, tag(activeRoom, JsonEnvelope.build("chat", user, activeRoom, msg, null, null, null)));
            }

        } catch (IOException e) {
            System.err.println("[WARN] " + user + " 연결 중 오류: " + e.getMessage());
        } finally {
            // 모든 가입 방에 퇴장 방송 후 leave
            if (pw != null) {
                for (String r : new HashSet<>(joinedRooms)) {
                    rooms.broadcast(r, tag(r, JsonEnvelope.build("system", user, r, user+" left", null, null, null)));
                    rooms.leave(r, pw);
                }
            }
            userDirectory.unregister(user); // 유저 해제
            try { if (pw != null) pw.close(); } catch (Exception ignored) {}
            try { if (br != null) br.close(); } catch (Exception ignored) {}
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    /** 방 입장 공통 처리 */
    private void joinRoom(String room, PrintWriter pw, boolean announce) {
        rooms.join(room, pw);
        joinedRooms.add(room);
        if (announce) rooms.broadcast(room, tag(room, JsonEnvelope.build("system", user, room, user+" joined", null, null, null)));
    }

    /** 출력 메시지에 방 태그 부착 */
    private String tag(String room, String json) {
        return "[" + room + "] " + json;
    }
}
