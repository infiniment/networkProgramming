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

    // 3. 확장 기능 명령어
    public static final String CMD_TYPING_START = "/typing start";  // 타이핑 시작
    public static final String CMD_TYPING_STOP = "/typing stop";    // 타이핑 중지
    public static final String CMD_BOMB = "/bomb";                  // 자폭 메시지
    public static final String CMD_SECRET = "/secret";              // 비밀 채팅

    // 4. 서버 응답 식별자 (Server -> Client)
    public static final String RESPONSE_ROOMS = "@rooms";            // 방 목록 응답
}