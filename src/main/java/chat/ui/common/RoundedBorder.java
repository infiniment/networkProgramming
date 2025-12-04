package chat.ui.common;

import javax.swing.border.EmptyBorder;
import javax.swing.JTextField;
import java.awt.*;

// RoundedBorder : 둥근 모서리 테두리
public class RoundedBorder extends EmptyBorder {
    private final int radius;
    private final Color normalColor;
    private final Color focusColor;

    public RoundedBorder(int radius, Color normalColor, Color focusColor) {
        super(10, 14, 10, 14); // 내용 패딩
        this.radius = radius;
        this.normalColor = normalColor;
        this.focusColor = focusColor;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (c instanceof JTextField && ((JTextField) c).hasFocus()) {
            g2.setColor(focusColor);
            g2.setStroke(new BasicStroke(2));
        } else {
            g2.setColor(normalColor);
            g2.setStroke(new BasicStroke(1));
        }
        g2.drawRoundRect(x + 1, y + 1, width - 3, height - 3, radius, radius);
        g2.dispose();
    }
}
