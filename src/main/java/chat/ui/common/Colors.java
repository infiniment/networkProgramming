package chat.ui.common;

import java.awt.Color;

// Colors : ui 전역에서 쓰는 토큰 모음
public final class Colors {
    private Colors(){}

    public static final Color PRIMARY         = new Color(255, 159, 64);   // 메인 포인트
    public static final Color PRIMARY_HOVER   = new Color(255, 140, 40);   // 버튼 hover
    public static final Color BG_COLOR        = new Color(240, 242, 245);  // 앱 배경
    public static final Color CARD_BG         = Color.WHITE;               // 카드/패널 배경
    public static final Color TEXT_PRIMARY    = new Color(31, 41, 55);     // 메인 텍스트
    public static final Color TEXT_SECONDARY  = new Color(120, 130, 140);  // 보조 텍스트
    public static final Color MY_BUBBLE       = PRIMARY;                   // 내 말풍선
    public static final Color OTHER_BUBBLE    = Color.WHITE;               // 상대 말풍선
    public static final Color INPUT_BG        = new Color(247, 249, 252);  // 인풋 배경
    public static final Color INPUT_BORDER    = new Color(254, 215, 170);  // 인풋 보더

    // --- 시크릿 모드 테마---
    public static final Color SECRET_BG              = new Color(18, 22, 28);
    public static final Color SECRET_CARD            = new Color(26, 31, 39);
    public static final Color SECRET_ACCENT          = new Color(120, 93, 255);   // 보라 톤 포커스
    public static final Color SECRET_TEXT_PRIMARY    = new Color(230, 235, 242);
    public static final Color SECRET_TEXT_SECONDARY  = new Color(160, 170, 185);
    public static final Color SECRET_INPUT_BG        = new Color(23, 28, 36);
    public static final Color SECRET_INPUT_BORDER    = new Color(80, 90, 120);
    public static final Color SECRET_MY_BUBBLE       = new Color(50, 60, 76);
    public static final Color SECRET_OTHER_BUBBLE    = new Color(36, 44, 57);
}
