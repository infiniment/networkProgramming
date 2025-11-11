package chat.ui.common;

import javax.swing.border.AbstractBorder;
import java.awt.*;

public class DashedRoundedBorder extends AbstractBorder {
    private final int radius;
    private final Color color;

    public DashedRoundedBorder(int radius, Color color) {
        this.radius = radius;
        this.color = color;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float[] dash = {6f, 4f};
        g2.setColor(color);
        g2.setStroke(new BasicStroke(
                2f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10f,
                dash,
                0f
        ));
        g2.drawRoundRect(x + 2, y + 2, width - 4, height - 4, radius, radius);
        g2.dispose();
    }
}
