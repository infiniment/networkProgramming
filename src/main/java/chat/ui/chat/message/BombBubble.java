package chat.ui.chat.message;

import chat.ui.common.Colors;
import chat.ui.fonts.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class BombBubble extends JPanel {
    public BombBubble(ImageIcon bombIcon, int seconds, String text, boolean mine) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel inner = new JPanel(new BorderLayout(10, 6)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 살짝 어두운 채워진 말풍선 + 은은한 외곽선
                g2.setColor(mine ? Colors.MY_BUBBLE : Colors.OTHER_BUBBLE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(0,0,0,40));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel icon = new JLabel(scaleBomb(bombIcon, 48));      // 🔼 32 → 48
        JLabel title = new JLabel("폭탄 메시지 · " + seconds + "초 후 폭발");
        title.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
        title.setForeground(Colors.TEXT_SECONDARY);

        JLabel body = new JLabel("<html>" + escape(text) + "</html>");
        body.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        body.setForeground(Colors.TEXT_PRIMARY);

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        north.setOpaque(false);
        north.add(icon);
        north.add(title);

        inner.add(north, BorderLayout.NORTH);
        inner.add(body, BorderLayout.CENTER);

        add(inner, BorderLayout.CENTER);
    }

    private static Icon scaleBomb(ImageIcon icon, int size) {
        Image img = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }
}
