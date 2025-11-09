package chat.server;

import chat.util.Constants;

/**
 * route(String raw)가 문자열을 파싱하고, 각 핸들러 메서드에서 ClientHandler의 컨텍스트(nickname, currentRoom, sendMessage(...))를 사용.
 * 실패/사용법 안내는 [System]으로 돌려주기.
 * 귓속말(/to)은 UserDirectory로 대상 찾기.
 * 언급(/@)은 room broadcast에 “멘션” 마킹(단, 실제 강조/알림은 클라 UI에서 추후 처리).
 */
public class CommandRouter {
    private final ClientHandler ctx;
    private final RoomManager roomManager;
    private final UserDirectory users;
    private final ChatServer server;

    private boolean secretMode = false;   // 현재 시크릿 모드 상태 on/off
    private String  secretSid  = null;    // 이번 시크릿 세션 식별자

    public CommandRouter(ClientHandler ctx, RoomManager roomManager, UserDirectory users, ChatServer server) {
        this.ctx = ctx;
        this.roomManager = roomManager;
        this.users = users;
        this.server = server;
    }

    void route(String raw) {
        String command = raw.trim();

        // 1) 빠른 매칭(정확 명령어)
        switch (command) {
            case Constants.CMD_HELP:        help(); return;
            case Constants.CMD_WHO:         who(); return;
            case Constants.CMD_SECRET:
                ctx.sendMessage("[System] 사용법: /secret on|off");
                return;
            case Constants.CMD_SECRET_ON:
                if (ctx.currentRoom() == null) { ctx.sendMessage("[System] 방에 입장 중이 아닙니다."); return; }
                if (!secretMode) {
                    secretMode = true;
                    // 세션 고유 ID 발급(충돌 위험 극히 낮음)
                    secretSid = java.util.UUID.randomUUID().toString();
                }
                ctx.sendMessage("[System] 비밀 채팅 모드 ON");
                return;
            case Constants.CMD_SECRET_OFF:
                if (!secretMode || secretSid == null) { ctx.sendMessage("[System] 비밀 모드가 아닙니다."); return; }
                // OFF 순간 방 전체에 “이 sid 메시지들 지워라” 이벤트 방송
                if (ctx.currentRoom() != null) {
                    ctx.currentRoom().broadcast(Constants.EVT_SECRET_CLEAR + " " + secretSid);
                }
                secretMode = false;
                secretSid  = null;
                ctx.sendMessage("[System] 비밀 채팅 모드 OFF");
                return;
        }

        // 2) 접두사/인자 기반 명령
        if (command.startsWith(Constants.CMD_SILENT + " ")) {
            silent(command.substring(Constants.CMD_SILENT.length()).trim());
            return;
        }
        if (command.startsWith(Constants.CMD_TO + " ")) {
            whisper(command.substring(Constants.CMD_TO.length()).trim());
            return;
        }
        if (command.startsWith(Constants.CMD_MENTION)) { // "/@닉 메시지"
            mention(command.substring(Constants.CMD_MENTION.length()).trim());
            return;
        }

        // 3) 이미 존재하는 명령(방 관련/서버 공통)은 ClientHandler 쪽에서 처리 (rooms, join, room.create, quit, bomb 등)
        // 여기서 알 수 없는 커맨드 메시지
        ctx.sendMessage("[System] 알 수 없는 명령어입니다: " + command);
    }

    private void help() {
        ctx.sendMessage(
                "[System] 사용 가능 : " +
                        String.join(" | ",
                                "/rooms", "/room.create [이름] [정원] [lock|open]", "/join [방]",
                                "/quit", "/to [닉네임] [메시지]", "/@닉네임 [메시지]", "/secret on|off", "/silent [메시지]",
                                "/bomb [초] [메시지]", "/who", "/gomoku", "/31"
                                )
        );
    }

    private void silent(String text) {
        if (text.isBlank()) { ctx.sendMessage("[System] 사용법: /silent [메시지]"); return; }
        if (ctx.currentRoom() == null) { ctx.sendMessage("[System] 방에 입장 중이 아닙니다."); return; }

        // [SILENT] 태그를 붙여 브로드캐스트 (클라에서 이 태그면 알림/배지/사운드 억제)
        String line = "[SILENT] " + ctx.nickname() + ": " + text;

        // 비밀모드와 병행 시(선택): [SECRET]도 함께 표시하고 싶다면 아래처럼
        // if (isSecretMode()) line = "[SECRET] " + line;

        ctx.currentRoom().broadcast(line);
    }

//    private void who() {
//        if (ctx.currentRoom() == null) { ctx.sendMessage("[System] 방에 입장 중이 아닙니다."); return; }
//        // Room 내부에 참가자 PrintWriter만 있어서 닉목록은 ClientHandler 쪽에서 소유/관리하는 게 깔끔하지만
//        // 간단히 브로드캐스트로 “현재 방 인원 확인해주세요” 대신, 서버가 알 수 있는 최소 정보만 출력.
//        ctx.sendMessage("[System] 현재 방: " + ctx.currentRoom().getName());
//        // 필요 시 UserDirectory와 Room의 participants를 매칭해 닉 추출 구조를 확장하세요.
//    }

    private void who() {
        if (ctx.currentRoom() == null) {
            ctx.sendMessage("[System] 방에 입장 중이 아닙니다.");
            return;
        }
        var nicks = ctx.currentRoom().participantNicknames(); // Java 10+ var
        ctx.sendMessage("[System] 현재 방: " + ctx.currentRoom().getName());
        ctx.sendMessage("[System] 참여자 (" + nicks.size() + "): " + String.join(", ", nicks));
    }



    private void whisper(String arg) {
        if (arg.isEmpty()) { ctx.sendMessage("[System] 사용법: /to [닉네임] [메시지]"); return; }
        String[] sp = arg.split("\\s+", 2);
        if (sp.length < 2) { ctx.sendMessage("[System] 사용법: /to [닉네임] [메시지]"); return; }
        String toNick = sp[0];
        String msg = sp[1];

        if (ctx.currentRoom() == null) {
            ctx.sendMessage("[System] 방에 입장 중이 아닙니다.");
            return;
        }

        ClientHandler target = server.getSession(toNick);
        if (target == null) {
            ctx.sendMessage("[System] 대상 사용자를 찾을 수 없습니다: " + toNick);
            return;
        }
        if (target.currentRoom() == null || target.currentRoom() != ctx.currentRoom()) {
            ctx.sendMessage("[System] 같은 방에 있는 사용자에게만 귓속말을 보낼 수 있습니다.");
            return;
        }

        // 상대방에게 메시지 보내기
        target.sendMessage("(whisper) " + ctx.nickname() + " → you: " + msg);
        // 발신자에게도 에코
        ctx.sendMessage("(whisper) you → " + toNick + ": " + msg);

    }

    private void mention(String arg) {
        if (ctx.currentRoom() == null) { ctx.sendMessage("[System] 방에 입장 중이 아닙니다."); return; }
        if (arg.isEmpty()) { ctx.sendMessage("[System] 사용법: /@닉네임 [메시지]"); return; }

        String[] sp = arg.split("\\s+", 2);
        String target = sp[0].replaceAll("^@", "");
        String text = sp.length > 1 ? sp[1] : "";

        ClientHandler targetH = server.getSession(target);
        if (targetH == null || targetH.currentRoom() != ctx.currentRoom()) {
            ctx.sendMessage("[System] @" + target + " 님이 현재 방에 없습니다.");
        }

        String line = "(mention @" + target + ") " + ctx.nickname() + ": " + text;
        ctx.currentRoom().broadcast(line);  // 클라에서 "(mention @닉)" 포함시 하이라이트 처리 가능
    }

    // 상태 노출 (필요 시 ClientHandler에서 읽어 UI/저장 등에 활용)
    public boolean isSecretMode()      { return secretMode; }
    public String  currentSecretSid()  { return secretSid; }

}
