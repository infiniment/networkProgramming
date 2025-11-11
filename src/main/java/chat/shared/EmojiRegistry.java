package chat.shared;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이모티콘 공용 레지스트리
 * - key  : 프로토콜에서 사용하는 코드 (":run:", ":doing:" ...)
 * - value: classpath 기준 리소스 경로 ("images/run.png")
 *
 * 서버: 코드 유효성 검사 / 목록 응답
 * 클라: 코드 -> 이미지 로딩 / 이모티콘 패널
 */
public final class EmojiRegistry {

    private static final Map<String, String> EMOJI;

    static {
        Map<String, String> e = new LinkedHashMap<>();

        // 전부 :code: 형태로 통일
        e.put(":run:",          "images/run.png");
        e.put(":doing:",        "images/doing.png");
        e.put(":funny:",        "images/funny.png");
        e.put(":giveup:",       "images/imoticonGiveup.png");
        e.put(":leave:",        "images/imoticonLeave.png");
        // 필요하면 여기 계속 추가

        EMOJI = Map.copyOf(e);
    }

    private EmojiRegistry() {}

    public static String findEmoji(String code) {
        return EMOJI.get(code);
    }

    public static Map<String, String> allEmojis() {
        return EMOJI;
    }
}
