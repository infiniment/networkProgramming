package chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatMessageRepository {

    // TODO: 너 DB 환경에 맞게 수정
    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb?serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASSWORD = "htg99127@@";


    static {
        try {
            // MySQL 드라이버 로드
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL Driver load fail", e);
        }
    }

    private static final String INSERT_SQL =
            "INSERT INTO chat_message (room_name, sender, message, is_secret, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())";

    public static void saveMessage(String roomName, String sender,
                                   String message, boolean isSecret) {
        if (roomName == null || sender == null || message == null) return;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setString(1, roomName);
            ps.setString(2, sender);
            ps.setString(3, message);
            ps.setBoolean(4, isSecret);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] 채팅 로그 저장 실패: " + e.getMessage());
        }
    }

    // 방 입장 시 최근 N개 불러올 때 사용할 메서드
    private static final String SELECT_RECENT_SQL =
            "SELECT sender, message, created_at " +
                    "FROM chat_message " +
                    "WHERE room_name = ? " +
                    "ORDER BY id DESC " +
                    "LIMIT ?";

    public static List<String> loadRecentMessages(String roomName, int limit) {
        List<String> result = new ArrayList<>();
        if (roomName == null) return result;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(SELECT_RECENT_SQL)) {

            ps.setString(1, roomName);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String msg = rs.getString("message");

                    // "[시간] 닉네임: 메시지" 형식으로 보내고 싶으면 여기서 포맷팅
                    result.add(sender + ": " + msg);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] 채팅 로그 조회 실패: " + e.getMessage());
        }

        // DB에서 최신순으로 가져왔으니까, 클라에 보낼 때는 오래된 것부터 보내고 싶으면 reverse
        Collections.reverse(result);
        return result;
    }
}
