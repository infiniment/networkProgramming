package chat.ui.fonts;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * FontManager
 * - 클래스패스(/resources/fonts/ttf/...)에서 TTF를 읽어와 Font 객체로 캐시
 * - 같은 (파일명, style, size) 조합은 재사용하여 퍼포먼스와 깜빡임 방지
 * - 폴백(Font "Dialog")까지 제공해 NPE 없이 안전하게 동작
 */
public final class FontManager {

    // 파일명 → base Font 캐시 (derive 전 원본)
    private static final Map<String, Font> BASE = new HashMap<>();

    // "파일명|style|size" → derive된 Font 캐시
    private static final Map<String, Font> DERIVED = new HashMap<>();

    private FontManager() {}

    /**
     * 요청한 폰트 조합을 반환(캐시 우선)
     * @param fontFileName  예) "BMHANNAAir_ttf.ttf"
     * @param style         Font.PLAIN / BOLD / ITALIC / (BOLD|ITALIC)
     * @param size          포인트 사이즈
     */
    public static Font get(String fontFileName, int style, int size) {
        final String key = fontFileName + "|" + style + "|" + size;

        // 1) 파생 폰트 캐시에 있으면 즉시 반환
        Font cached = DERIVED.get(key);
        if (cached != null) return cached;

        try {
            // 2) base 폰트 캐시 확인
            Font base = BASE.get(fontFileName);

            // 3) base가 없다면 클래스패스에서 로딩
            if (base == null) {
                String path = "fonts/ttf/" + fontFileName; // /resources/fonts/ttf 아래에 둔다
                try (InputStream is = FontManager.class.getClassLoader().getResourceAsStream(path)) {
                    if (is != null) {
                        base = Font.createFont(Font.TRUETYPE_FONT, is);
                        BASE.put(fontFileName, base);
                    }
                }
            }

            // 4) base가 있다면 해당 스타일/사이즈로 파생 후 캐시
            if (base != null) {
                Font derived = base.deriveFont(style, (float) size);
                DERIVED.put(key, derived);
                return derived;
            }
        } catch (Exception ignore) {
            // 폴백으로 처리
        }

        // 5) 로딩 실패 시 시스템 폰트 폴백 (이 조합도 캐시)
        Font fallback = new Font("Dialog", style, size);
        DERIVED.put(key, fallback);
        return fallback;
    }

    /**
     * 부팅 시점에 자주 쓰는 폰트 조합들을 미리 준비해 폰트 로딩 지연을 방지
     */
    public static void preload() {
        get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10);
        get("BMHANNAAir_ttf.ttf", Font.PLAIN, 11);
        get("BMHANNAAir_ttf.ttf", Font.PLAIN, 12);
        get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13);
        get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14);
        get("BMHANNAAir_ttf.ttf", Font.BOLD, 12);
        get("BMDOHYEON_ttf.ttf", Font.BOLD, 14);
        get("BMDOHYEON_ttf.ttf", Font.BOLD, 16);
    }
}
