package chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatRoomRepository {

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

    // 1) 서버 시작 시 전체 방 목록 불러오기
    public static List<Room> loadAllRooms() {
        String sql = "SELECT name, capacity, locked, password, owner_name FROM chat_room";
        List<Room> result = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String name = rs.getString("name");
                int capacity = rs.getInt("capacity");
                boolean locked = rs.getBoolean("locked");
                String password = rs.getString("password"); // null 가능
                String ownerName = rs.getString("owner_name");

                Room room = new Room(name, capacity, locked, password, ownerName);
                result.add(room);
            }

        } catch (SQLException e) {
            System.err.println("[DB] chat_room 로드 실패: " + e.getMessage());
        }

        return result;
    }

    // 2) 방을 insert 또는 update (ON DUPLICATE KEY 사용)
    public static void upsertRoom(Room room) {
        String sql =
                "INSERT INTO chat_room (name, capacity, locked, password, owner_name) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  capacity   = VALUES(capacity), " +
                        "  locked     = VALUES(locked), " +
                        "  password   = VALUES(password), " +
                        "  owner_name = VALUES(owner_name)";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, room.getName());
            ps.setInt(2, room.getCapacity());
            ps.setBoolean(3, room.isLocked());


            // 잠금 방이면 비밀번호, 아니면 null
            String pwd = room.isLocked() ? room.getPassword() : null;
            ps.setString(4, pwd);

            ps.setString(5, room.getOwnerName());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] chat_room upsert 실패: " + e.getMessage());
        }
    }

    // 3) 방 삭제시 DB에서도 삭제 (옵션)
    public static void deleteRoom(String roomName) {
        String sql = "DELETE FROM chat_room WHERE name = ?";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roomName);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] chat_room 삭제 실패: " + e.getMessage());
        }
    }
}
