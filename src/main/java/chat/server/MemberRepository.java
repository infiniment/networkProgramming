package chat.server;

import java.sql.*;

public class MemberRepository {


    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb?serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASSWORD = "htg99127@@";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL Driver load fail", e);
        }
    }

    // 이름으로 멤버를 찾고, 없으면 새로 생성 후 id 반환
    public static long findOrCreateByName(String name) {
        Long existing = findIdByName(name);
        if (existing != null) return existing;
        return insert(name);
    }

    public static Long findIdByName(String name) {
        String sql = "SELECT id FROM member WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] member 조회 실패: " + e.getMessage());
        }
        return null;
    }

    private static long insert(String name) {
        String sql = "INSERT INTO member (name) VALUES (?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            System.err.println("[DB] member 생성 실패: " + e.getMessage());
        }
        return -1L;
    }
}
