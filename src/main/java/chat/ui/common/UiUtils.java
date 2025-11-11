package chat.ui.common;

import javax.swing.*;
import java.awt.*;

/**
 * UiUtils
 * - 공통 UI 보조 기능(오토 스크롤/리페인트, 상태 점 아이콘)
 */
public final class UiUtils {
    private UiUtils(){}

    /**
     * 채팅 영역 변경 시 EDT에서 안전하게 UI 갱신 + 자동 스크롤 다운
     * @param chatContainer  메시지들이 들어가는 컨테이너(JPanel)
     * @param chatScroll     스크롤(자동 하단 이동용)
     * @param uiChanges      변경할 UI 작업(람다)
     */
    public static void commitChatUpdate(JPanel chatContainer, JScrollPane chatScroll, Runnable uiChanges) {
        if (SwingUtilities.isEventDispatchThread()) {
            uiChanges.run();
            chatContainer.revalidate();
            chatContainer.repaint();

            // 다음 틱에서 스크롤바를 최하단으로 이동
            SwingUtilities.invokeLater(() -> {
                JScrollBar bar = chatScroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            });
        } else {
            // EDT가 아니면 EDT에 위임
            SwingUtilities.invokeLater(() -> commitChatUpdate(chatContainer, chatScroll, uiChanges));
        }
    }



    /**
     * 연결 상태를 나타내는 작은 동그라미 아이콘(초록/빨강 등)
     * @param color  점 색상
     */
    public static Icon makeStatusIcon(Color color) {
        final int size = 10;
        return new Icon() {
            public int getIconWidth()  { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, size, size);
                g2.setColor(Color.DARK_GRAY);
                g2.drawOval(x, y, size, size);
                g2.dispose();
            }
        };
    }

    public static void scrollToBottom(JScrollPane scroll) {
        if (scroll == null) return;
        Runnable r = () -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            if (bar != null) bar.setValue(bar.getMaximum());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            // 바로 한 번, 그리고 레이아웃 완료 후 한 번 더
            r.run();
            SwingUtilities.invokeLater(r);
        } else {
            SwingUtilities.invokeLater(() -> {
                r.run();
                SwingUtilities.invokeLater(r);
            });
        }
    }
}
