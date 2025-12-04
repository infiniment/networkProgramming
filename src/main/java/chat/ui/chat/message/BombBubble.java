package chat.ui.chat.message;

import chat.ui.common.Colors;
import chat.ui.fonts.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class BombBubble extends JPanel {
    // 폭탄 메시지 UI를 설정하고 초기화하는 부분
    public BombBubble(ImageIcon bombIcon, int seconds, String text, boolean mine) {
        setOpaque(false); // 투명 설정
        setLayout(new BorderLayout()); // 레이아웃 설정
        setBorder(new EmptyBorder(10, 12, 10, 12)); // 내부 여백 설정

        // 폭탄 메시지 내부 디자인을 담당하는 패널
        JPanel inner = new JPanel(new BorderLayout(10, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                // 메시지의 배경과 테두리를 그리는 부분
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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

        // 폭탄 아이콘과 타이틀을 표시하는 부분
        JLabel icon = new JLabel(scaleBomb(bombIcon, 48));
        JLabel title = new JLabel("폭탄 메시지 · " + seconds + "초 후 폭발");
        title.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
        title.setForeground(Colors.TEXT_SECONDARY);

        // 메시지 본문을 설정하는 부분
        JLabel body = new JLabel("<html>" + escape(text) + "</html>");
        body.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        body.setForeground(Colors.TEXT_PRIMARY);

        // 타이틀과 아이콘을 왼쪽에 배치하는 부분
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        north.setOpaque(false);
        north.add(icon);
        north.add(title);

        inner.add(north, BorderLayout.NORTH);
        inner.add(body, BorderLayout.CENTER);

        add(inner, BorderLayout.CENTER);
    }

    // 폭탄 아이콘 크기 조정 함수
    private static Icon scaleBomb(ImageIcon icon, int size) {
        Image img = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    // HTML 태그를 안전하게 변환하는 함수
    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }
}
