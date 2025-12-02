package chat.ui.fonts;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class FontManager {
    private static final Map<String, Font> BASE = new HashMap<>();
    private static final Map<String, Font> DERIVED = new HashMap<>();

    private FontManager() {}

    public static Font get(String fontFileName, int style, int size) {
        final String key = fontFileName + "|" + style + "|" + size;

        Font cached = DERIVED.get(key);
        if (cached != null) return cached;

        try {
            Font base = BASE.get(fontFileName);

            if (base == null) {
                String path = "fonts/ttf/" + fontFileName;
                try (InputStream is = FontManager.class.getClassLoader().getResourceAsStream(path)) {
                    if (is != null) {
                        base = Font.createFont(Font.TRUETYPE_FONT, is);
                        BASE.put(fontFileName, base);
                    }
                }
            }

            if (base != null) {
                Font derived = base.deriveFont(style, (float) size);
                DERIVED.put(key, derived);
                return derived;
            }
        } catch (Exception ignore) {
        }

        Font fallback = new Font("Dialog", style, size);
        DERIVED.put(key, fallback);
        return fallback;
    }

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
