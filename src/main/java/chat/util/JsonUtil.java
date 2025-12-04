package chat.util;

import chat.shared.model.RoomDto;
import java.util.List;
import java.util.Map;

public class JsonUtil {

    private JsonUtil() {}

    public static String roomsToJson(List<RoomDto> rooms) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < rooms.size(); i++) {
            RoomDto room = rooms.get(i);

            // 각 RoomDto 객체를 JSON 문자열로 변환
            sb.append(roomObjectToJson(room));

            if (i < rooms.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private static String roomObjectToJson(RoomDto room) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append("\"name\":\"").append(escapeJson(room.name)).append("\",");

        sb.append("\"participants\":").append(room.participants).append(",");

        sb.append("\"capacity\":").append(room.capacity).append(",");

        sb.append("\"active\":").append(room.active).append(",");

        sb.append("\"locked\":").append(room.locked);

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public static String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0, n = map.size();
        for (Map.Entry<String,String> e : map.entrySet()) {
            sb.append("\"").append(escapeJson(e.getKey())).append("\":")
                    .append("\"").append(escapeJson(e.getValue())).append("\"");
            if (++i < n) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}