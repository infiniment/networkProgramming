package chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatMessageRepository {

    // TODO: DB 환경에 맞게 수정
    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb?serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASSWORD = "1234";

    static {
        try {
            // MySQL 드라이버 로드
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // 드라이버가 없으면 DB 작업 자체가 불가능하므로 런타임 예외로 터뜨림
            throw new RuntimeException("MySQL Driver load fail", e);
        }
    }

    /**
     *  채팅 메시지 저장용 SQL
     * - room_name: 방 이름(또는 방 식별자 역할)
     * - sender: 보낸 사람 닉네임/ID
     * - message: 메시지 본문
     * - is_secret: 비밀메시지 여부
     * - created_at: 저장 시각(NOW()로 DB에서 자동 기록)
     */
    private static final String INSERT_SQL =
            "INSERT INTO chat_message (room_name, sender, message, is_secret, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())";

    /**
     *  saveMessage()
     * - 채팅 한 줄을 DB(chat_message 테이블)에 저장하는 함수
     * - null 입력(방/보낸이/메시지)이면 저장하지 않고 return
     * - try-with-resources로 Connection/PreparedStatement 자동 close(자원 누수 방지)
     *
     * @param roomName  저장할 채팅방 이름
     * @param sender    보낸 사람(닉네임)
     * @param message   메시지 내용
     * @param isSecret  비밀 메시지인지 여부
     */
    public static void saveMessage(String roomName, String sender,
                                   String message, boolean isSecret) {
        // 필수값이 없으면 DB 저장 시도 자체를 안 함 (NPE/SQL 오류 방지)
        if (roomName == null || sender == null || message == null) return;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            // '?' 파라미터에 값 바인딩 (SQL Injection 방지 + 타입 안전)
            ps.setString(1, roomName);
            ps.setString(2, sender);
            ps.setString(3, message);
            ps.setBoolean(4, isSecret);

            // INSERT 실행
            ps.executeUpdate();

        } catch (SQLException e) {
            // DB 오류가 나도 서버가 죽지 않게 로그만 출력
            System.err.println("[DB] 채팅 로그 저장 실패: " + e.getMessage());
        }
    }

    /**
     *   방 입장 시 최근 메시지 N개 불러오는 SQL
     * - 선택 컬럼: sender, message, created_at
     * - 조건: room_name = ?
     * - 정렬: id DESC (최신 메시지가 먼저 오게)
     * - 제한: LIMIT ? (최근 N개)
     */
    private static final String SELECT_RECENT_SQL =
            "SELECT sender, message, created_at " +
                    "FROM chat_message " +
                    "WHERE room_name = ? " +
                    "ORDER BY id DESC " +
                    "LIMIT ?";

    /**
     *  loadRecentMessages()
     * - 특정 채팅방(roomName)의 최근 메시지 limit개를 DB에서 조회해 List<String>으로 반환
     * - DB에서는 최신순(DESC)으로 가져오므로, 화면에 "오래된 것 -> 최신" 순으로 보여주려면 reverse 함
     *
     * @param roomName 조회할 채팅방 이름
     * @param limit    가져올 최근 메시지 개수
     * @return         (오래된 것부터) "sender: message" 문자열 목록
     */
    public static List<String> loadRecentMessages(String roomName, int limit) {
        List<String> result = new ArrayList<>();
        if (roomName == null) return result;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(SELECT_RECENT_SQL)) {

            // room_name, limit 바인딩
            ps.setString(1, roomName);
            ps.setInt(2, limit);

            // SELECT 실행 결과 (ResultSet)
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String msg = rs.getString("message");
                    // Timestamp ts = rs.getTimestamp("created_at"); // 시간까지 쓰고 싶으면 이렇게 꺼냄

                    // 반환 포맷 구성: "닉네임: 메시지"
                    // "[시간] 닉네임: 메시지" 같은 포맷은 여기서 만들어서 add 하면 됨
                    result.add(sender + ": " + msg);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] 채팅 로그 조회 실패: " + e.getMessage());
        }

        // DB에서 최신순(id DESC)으로 가져왔으니,
        // UI/클라 입장에선 오래된 것부터 보여주기 위해 뒤집어서 반환
        Collections.reverse(result);
        return result;
    }
}
