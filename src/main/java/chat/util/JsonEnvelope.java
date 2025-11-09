    package chat.util;

    import com.fasterxml.jackson.databind.ObjectMapper;

    import java.util.LinkedHashMap;
    import java.util.Map;

    /**
     * JsonEnvelope
     * ---------------------------------------------------------
     * 클래스 개요
     * - 채팅 메시지를 JSON 문자열 형태로 포장(직렬화)하는 유틸리티 클래스.
     * - 서버와 클라이언트 간 통신을 표준화하기 위해 사용된다.
     *
     * 예: 아래처럼 사람이 읽기 좋은 JSON 형태로 변환됨.
     *  {
     *    "v": 1,
     *    "type": "chat",
     *    "from": "alice",
     *    "room": "lobby",
     *    "text": "안녕하세요 :)",
     *    "to": null,
     *    "status": null,
     *    "ttlMs": null
     *  }
     *
     * 주요 용도
     * - 일반 채팅 메시지 (type = "chat")
     * - 시스템 알림 (type = "system")
     * - 타이핑 상태 전송 (type = "typing")
     * - 귓속말/비밀 메시지 (type = "whisper")
     * - 자폭 메시지 (type = "ttl")
     * - 게임/이모티콘 이벤트 등 확장 가능
     *
     * 필드 설명
     * - v       : 메시지 버전 (현재 1)
     * - type    : 메시지 종류 (chat/system/whisper/typing/...)
     * - from    : 보낸 사용자 닉네임
     * - room    : 해당 메시지가 속한 방 이름
     * - text    : 실제 메시지 내용
     * - to      : 귓속말 대상 사용자 (필요 시)
     * - status  : typing 등 상태 값 (start/stop 등)
     * - ttlMs   : 자폭 메시지 유지 시간 (밀리초 단위)
     *
     * 예외 처리
     * - 변환 중 오류가 발생하면 안전하게 {"type":"system","text":"encode_error"}를 반환.
     */
    public class JsonEnvelope {
        /** Jackson의 ObjectMapper 인스턴스 (JSON 직렬화 담당) */
        private static final ObjectMapper MAPPER = new ObjectMapper();

        /**
         * 인스턴스 생성 방지를 위해 private 생성자 (정적 유틸리티 클래스이므로 객체를 만들 필요 없음)
         */
        private JsonEnvelope() {}

        /**
         * build()
         * -----------------------------------------------------
         * 주어진 정보들을 JSON 형식 문자열로 변환한다.
         * (null 값은 자동으로 생략되어 간결한 JSON이 생성된다.)
         *
         * @param type   메시지 종류 ("chat", "system", "typing", "whisper" 등)
         * @param from   보낸 사람 닉네임
         * @param room   방 이름
         * @param text   메시지 내용
         * @param to     귓속말 대상 닉네임 (없으면 null)
         * @param status 상태 정보 (예: "start"/"stop" 등, 없으면 null)
         * @param ttlMs  자폭 메시지 타이머 (밀리초 단위, 없으면 null)
         * @return JSON 문자열 형태로 직렬화된 메시지
         */
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

        // 2) 숫자 ttlMs & enum 타입 지원 오버로드
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

        // 필요하면 내부 enum(혹은 기존 MessageType 재사용)
        public enum MessageType {
            CHAT("chat"), SYSTEM("system"), TYPING("typing"),
            WHISPER("whisper"), TTL("ttl"), EMOJI("emoji"),
            STICKER("sticker"), GAME("game");
            private final String v;
            MessageType(String v){ this.v = v; }
            public String value(){ return v; }
        }
    }
