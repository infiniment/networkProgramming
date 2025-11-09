package chat.ui.common;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * RoundedPanel
 * - 둥근 모서리 배경을 그리는 패널
 * - setBackground(...)로 배경색 적용
 */
public class RoundedPanel extends JPanel {
    private final int radius;

    public RoundedPanel(int radius) {
        this.radius = radius;
        setOpaque(false); // 우리가 직접 배경을 그림
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        g2.dispose();
        super.paintComponent(g);
    }
}
