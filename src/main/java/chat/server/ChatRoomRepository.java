package chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatRoomRepository {

    // DB 접속 정보(환경에 맞게 수정)
    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb?serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASSWORD = "1234";

    // 클래스 로딩 시 MySQL JDBC 드라이버 로드
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL Driver load fail", e);
        }
    }

    // 서버 시작 시 DB에 저장된 모든 채팅방 정보를 읽어 Room 리스트로 반환
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
                String password = rs.getString("password"); // 잠금방이 아니면 null일 수 있음
                String ownerName = rs.getString("owner_name");

                Room room = new Room(name, capacity, locked, password, ownerName);
                result.add(room);
            }

        } catch (SQLException e) {
            System.err.println("[DB] chat_room 로드 실패: " + e.getMessage());
        }

        return result;
    }

    // 방 정보를 DB에 저장하되, 같은 name이 있으면 INSERT 대신 UPDATE로 갱신
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

            // 잠금방이면 비밀번호를 저장하고, 아니면 null로 저장
            String pwd = room.isLocked() ? room.getPassword() : null;
            ps.setString(4, pwd);

            ps.setString(5, room.getOwnerName());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] chat_room upsert 실패: " + e.getMessage());
        }
    }

    // 방 삭제 시 DB에서도 해당 방 레코드를 삭제
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
