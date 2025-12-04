    package chat.util;

    import com.fasterxml.jackson.databind.ObjectMapper;

    import java.util.LinkedHashMap;
    import java.util.Map;

    public class JsonEnvelope {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private JsonEnvelope() {}

        public static String build(String type, String from, String room, String text,
                                   String to, String status, String ttlMs) {
            try{
                // 순서를 유지하기 위해 LinkedHashMap 사용
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("v", 1);  // 메시지 버전
                m.put("type", type);    // 메시지 타입
                m.put("from", from);    // 보낸 사람
                m.put("room", room);    // 방 이름

                // null 값은 JSON에 포함하지 않음
                if (text != null)
                    m.put("text", text);
                if (to != null)
                    m.put("to", to);
                if (status != null)
                    m.put("status", status);
                if (ttlMs != null)
                    m.put("ttlMs", ttlMs);

                // Map을 JSON 문자열로 직렬화
                return MAPPER.writeValueAsString(m);
            }catch (Exception e) {
                // 변환 실패 시 기본적인 시스템 메시지 반환
                return "{\"v\":1,\"type\":\"system\",\"text\":\"encode_error\"}";
            }
        }

        // 숫자 ttlMs & enum 타입 지원 오버로드
        public static String build(MessageType type, String from, String room, String text,
                                   String to, String status, Long ttlMs) {
            try {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("v", 1);
                m.put("type", type.value()); // enum -> string
                m.put("from", from);
                m.put("room", room);
                if (text != null)   m.put("text", text);
                if (to != null)     m.put("to", to);
                if (status != null) m.put("status", status);
                if (ttlMs != null)  m.put("ttlMs", ttlMs);
                return MAPPER.writeValueAsString(m);
            } catch (Exception e) {
                return "{\"v\":1,\"type\":\"system\",\"text\":\"encode_error\"}";
            }
        }

        // 필요하면 내부 enum
        public enum MessageType {
            CHAT("chat"), SYSTEM("system"), TYPING("typing"),
            WHISPER("whisper"), TTL("ttl"), EMOJI("emoji"),
            STICKER("sticker"), GAME("game");
            private final String v;
            MessageType(String v){ this.v = v; }
            public String value(){ return v; }
        }
    }
