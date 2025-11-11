package chat.util;

/**
 *~
 * 클라이언트와 서버 간의 일관된 통신 규약을 유지하는 데 사용됩니다.
 */
public class Constants {
    // ========== 1. 통신 기본 설정 ==========
    public static final int DEFAULT_PORT = 5959;
    public static final String DEFAULT_HOST = "localhost";

    // ========== 2. 채팅 명령어 (Client -> Server) ==========
    public static final String CMD_ROOMS_LIST = "/rooms";
    public static final String CMD_ROOM_CREATE = "/room.create";
    public static final String CMD_JOIN_ROOM = "/join";
    public static final String CMD_QUIT = "/quit";
    public static final String CMD_TYPING_START = "/typing start";
    public static final String CMD_TYPING_STOP = "/typing stop";
    public static final String CMD_BOMB = "/bomb";

    // ========== 3. 슬래시 명령어 ==========
    public static final String CMD_HELP = "/help";
    public static final String CMD_WHO = "/who";
    public static final String CMD_TO = "/to";
    public static final String CMD_MENTION = "/@";
    public static final String CMD_SECRET = "/secret";
    public static final String CMD_SECRET_ON = "/secret on";
    public static final String CMD_SECRET_OFF = "/secret off";
    public static final String CMD_SILENT = "/silent";

    // ========== 4. 미니게임 명령어 ==========
    public static final String CMD_GOMOKU = "/gomoku";
    public static final String CMD_31 = "/31";
    public static final String CMD_GAME_JOIN = "/game.join";
    public static final String CMD_GAME_MOVE = "/game.move";
    public static final String CMD_GAME_QUIT = "/game.quit";

    // ========== 5. 서버 응답 (Server -> Client) ==========
    public static final String RESPONSE_ROOMS = "@rooms";
    public static final String RESPONSE_GAME_WAITING = "@game:waiting";
    public static final String RESPONSE_GAME_START = "@game:start";
    public static final String RESPONSE_GAME_END = "@game:end";

    // ========== 6. 서버→클라 이벤트 ==========
    public static final String EVT_SECRET_MSG = "@secret:msg";
    public static final String EVT_SECRET_ON    = "@secret:on";   // "@secret:on {sid} {hostNick}"
    public static final String EVT_SECRET_OFF   = "@secret:off";  // "@secret:off {sid} {hostNick}"
    public static final String EVT_SECRET_CLEAR = "@secret:clear";
    public static final String EVT_BOMB         = "@bomb";

    // ========== 7. 미디어 패킷 (이모티콘/스티커) ==========
    public static final String PKG_EMOJI = "@PKG_EMOJI";
    public static final String PKG_STICKER = "@PKG_STICKER";
}