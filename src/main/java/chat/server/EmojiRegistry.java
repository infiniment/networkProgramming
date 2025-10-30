package chat.server;

import java.util.Map;

public class EmojiRegistry {

    // 이런식으로 경로 넣고 하면됩니다.
    private static final Map<String,String> EMOJI = Map.of(
            ":smile:",  "res://emoji/smile.png",
            ":sad:",    "res://emoji/sad.png",
            ":heart:",  "res://emoji/heart.png"
    );
    private static final Map<String,String> STICKER = Map.of(
            "bear_hello", "res://sticker/bear_hello.png",
            "duck_hi",    "res://sticker/duck_hi.png"
    );

    private EmojiRegistry() {}

    public static String findEmoji(String code)   { return EMOJI.get(code); }
    public static String findSticker(String name) { return STICKER.get(name); }

    public static Map<String,String> allEmojis()   { return EMOJI; }
    public static Map<String,String> allStickers() { return STICKER; }
}
