package chat.ui.common;

import java.awt.Color;

/**
 * Colors
 * - UI 전역에서 쓰는 색상 토큰 모음
 * - 팔레트를 한 곳에서 관리하면 테마 변경/일관성이 쉬움
 */
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
}
