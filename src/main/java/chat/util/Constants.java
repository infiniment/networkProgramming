package chat.util;

/**
 * 프로젝트 전체에서 사용되는 상수(포트, 명령어 등)를 정의합니다.
 * 클라이언트와 서버 간의 일관된 통신 규약을 유지하는 데 사용됩니다.
 */
public class Constants {
    // 1. 통신 기본 설정
    public static final int DEFAULT_PORT = 5959;
    public static final String DEFAULT_HOST = "localhost";

    // 2. 서버로 전송하는 명령어 (Client -> Server)
    public static final String CMD_ROOMS_LIST = "/rooms";            // 방 목록 요청
    public static final String CMD_ROOM_CREATE = "/room.create";    // 방 생성
    public static final String CMD_JOIN_ROOM = "/join";             // 방 입장
    public static final String CMD_QUIT = "/quit";                  // 종료 및 퇴장

    public static final String CMD_TYPING_START = "/typing start";  // 타이핑 시작
    public static final String CMD_TYPING_STOP = "/typing stop";    // 타이핑 중지

    // 3. 확장 기능 명령어
    public static final String CMD_HELP         = "/help";         // 슬래쉬 명령어 보여주기
    public static final String CMD_BOMB = "/bomb";                  // 자폭 메시지
    public static final String CMD_SECRET = "/secret";              // 비밀 채팅
    public static final String CMD_WHO          = "/who";           // 현재 방 인원 목록
    public static final String CMD_TO           = "/to";           // /to 닉 메시지  (귓속말)
    public static final String CMD_MENTION      = "/@";            // /@닉 메시지    (언급)
    public static final String CMD_SECRET_ON    = "/secret on";
    public static final String CMD_SECRET_OFF   = "/secret off";
    public static final String CMD_SILENT       = "/silent";
    public static final String CMD_GOMOKU = "/gomoku";  // 오목 게임 실행
    public static final String CMD_31  = "/31"; // 베스킨라빈스 게임 실행

    // 4. 서버 응답 식별자 (Server -> Client)
    public static final String RESPONSE_ROOMS = "@rooms";            // 방 목록 응답

    // --- 서버→클라 UI 제어 이벤트(유저가 치지 않음) ---
    public static final String EVT_SECRET_MSG   = "@secret:msg";   // @secret:msg <sid> <닉: 메시지>
    public static final String EVT_SECRET_CLEAR = "@secret:clear"; // @secret:clear <sid>
}