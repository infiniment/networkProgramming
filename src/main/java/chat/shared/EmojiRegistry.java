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
    private static final Map<String, java.util.List<String>> CATEGORIES;

    static {
        Map<String, String> e = new LinkedHashMap<>();

        // 기본 셋
        e.put(":coong1:", "images/coong/coong1.png");
        e.put(":coong2:", "images/coong/coong2.png");
        e.put(":coong3:", "images/coong/coong3.png");
        e.put(":coong4:", "images/coong/coong4.png");
        e.put(":coong5:", "images/coong/coong5.png");

        e.put(":bear1:", "images/bear/bear1.png");
        e.put(":bear2:", "images/bear/bear2.png");
        e.put(":bear3:", "images/bear/bear3.png");
        e.put(":bear4:", "images/bear/bear4.png");
        e.put(":bear5:", "images/bear/bear5.png");
        e.put(":bear6:", "images/bear/bear6.png");
        e.put(":bear7:", "images/bear/bear7.png");
        e.put(":bear8:", "images/bear/bear8.png");
        e.put(":bear9:", "images/bear/bear9.png");
        e.put(":bear10:", "images/bear/bear10.png");

        e.put(":dragon1:", "images/dragon/dragon1.png");
        e.put(":dragon2:", "images/dragon/dragon2.png");
        e.put(":dragon3:", "images/dragon/dragon3.png");
        e.put(":dragon4:", "images/dragon/dragon4.png");
        e.put(":dragon5:", "images/dragon/dragon5.png");
        e.put(":dragon6:", "images/dragon/dragon6.png");
        e.put(":dragon7:", "images/dragon/dragon7.png");
        e.put(":dragon8:", "images/dragon/dragon8.png");
        e.put(":dragon9:", "images/dragon/dragon9.png");
        e.put(":dragon10:", "images/dragon/dragon10.png");
        e.put(":dragon11:", "images/dragon/dragon11.png");
        e.put(":dragon12:", "images/dragon/dragon12.png");

        EMOJI = Map.copyOf(e);

        // 카테고리 구성 (원하는 대로 묶기)
        Map<String, java.util.List<String>> cats = new LinkedHashMap<>();
        cats.put("쿵야", java.util.List.of(":coong1:", ":coong2:", ":coong3:", ":coong4:", ":coong5:"));
        cats.put("뚱뚱한 곰", java.util.List.of(":bear1:", ":bear2:", ":bear3:", ":bear4:", ":bear5:",":bear6:", ":bear7:", ":bear8:", ":bear9:", ":bear10:"));
        cats.put("귀여운 용", java.util.List.of(":dragon1:", ":dragon2:", ":dragon3:", ":dragon4:", ":dragon5:",":dragon6:", ":dragon7:", ":dragon8:", ":dragon9:", ":dragon10:", ":dragon11:", ":dragon12:"));

        CATEGORIES = Map.copyOf(cats);
    }

    private EmojiRegistry() {}

    public static String findEmoji(String code) {
        return EMOJI.get(code);
    }
    public static Map<String, String> allEmojis() {
        return EMOJI;
    }

    // 카테고리 API
    public static Map<String, java.util.List<String>> categories() { return CATEGORIES; }
}
