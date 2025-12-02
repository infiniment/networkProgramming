package chat.ui.common;

import javax.swing.*;
import java.awt.*;

// UiUtils : 공통 UI 보조 기능

public final class UiUtils {
    private UiUtils(){}

    public static void commitChatUpdate(JPanel chatContainer, JScrollPane chatScroll, Runnable uiChanges) {
        if (SwingUtilities.isEventDispatchThread()) {
            uiChanges.run();
            chatContainer.revalidate();
            chatContainer.repaint();

            SwingUtilities.invokeLater(() -> {
                JScrollBar bar = chatScroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            });
        } else {
            SwingUtilities.invokeLater(() -> commitChatUpdate(chatContainer, chatScroll, uiChanges));
        }
    }

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
