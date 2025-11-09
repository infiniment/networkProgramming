package chat.util;

import chat.shared.model.RoomDto;
import java.util.List;
import java.util.Map;

/**
 * [Non-Library] 서버에서 클라이언트에게 전송할 JSON 문자열을 수동으로 생성하는 유틸리티입니다.
 * 외부 라이브러리(Gson, Jackson 등)를 사용하지 않고 순수 Java의 StringBuilder로 구현됩니다.
 */
public class JsonUtil {

    private JsonUtil() {}

    /**
     * List<RoomDto>를 JSON 배열 문자열로 수동 변환합니다.
     * RoomListFrame의 수동 파싱 로직과 일치하는 JSON 형식을 생성합니다.
     * * @param rooms RoomDto 리스트
     * @return JSON 배열 문자열 (예: [{"name":"A", "participants":1, ...}, {...}] )
     */
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

    /**
     * 단일 RoomDto 객체를 JSON 문자열로 변환합니다.
     */
    private static String roomObjectToJson(RoomDto room) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // name (String)
        sb.append("\"name\":\"").append(escapeJson(room.name)).append("\",");

        // participants (int)
        sb.append("\"participants\":").append(room.participants).append(",");

        // capacity (int)
        sb.append("\"capacity\":").append(room.capacity).append(",");

        // active (boolean) - RoomListFrame의 파싱 로직에 맞춰 true/false 문자열로 출력
        sb.append("\"active\":").append(room.active).append(",");

        // locked (boolean)
        sb.append("\"locked\":").append(room.locked);

        sb.append("}");
        return sb.toString();
    }

    /**
     * JSON 값 내의 특수 문자(따옴표 등)를 이스케이프 처리합니다.
     */
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

    // 클라이언트가 서버로 메시지를 보낼 때 사용될 수 있는 범용 to-json 메서드 (선택 사항)
    /*
    public static String messageToJson(String type, String from, String text) {
        return String.format("{\"type\":\"%s\",\"from\":\"%s\",\"text\":\"%s\"}",
                             type, escapeJson(from), escapeJson(text));
    }
    */
}