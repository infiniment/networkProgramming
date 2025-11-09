package chat.ui.main;

import chat.client.ChatClient;
import chat.util.Constants; // <--- 1. Constants Import 추가

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;

/**
 * 로그인 화면
 * - 가운데 정렬
 * - 한글 폰트 처리
 * - 둥근 모서리 디자인
 * - 호버 효과
 */
public class LoginFrame extends JFrame {
    // 색상 팔레트 - 밝은 주황색 테마
    private static final Color PRIMARY = new Color(255, 159, 64);       // 밝은 주황색
    private static final Color PRIMARY_HOVER = new Color(255, 140, 40); // 약간 진한 주황색
    private static final Color BG_COLOR = new Color(255, 247, 237);     // 연한 주황 배경
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(31, 41, 55);
    private static final Color TEXT_SECONDARY = new Color(255, 159, 64); // 밝은 주황색 텍스트
    private static final Color INPUT_BORDER = new Color(254, 215, 170); // 연한 주황 테두리
    private static final Color INPUT_FOCUS = new Color(255, 159, 64);    // 밝은 주황색 포커스

    private JTextField tfNickname;
    private JTextField tfHost;
    private JTextField tfPort;
    private JButton btnStart;

    public LoginFrame() {
        setTitle("MultiRoom Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 650);
        setLocationRelativeTo(null);
        setResizable(false);

        // 메인 배경 패널
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // 중앙 카드 패널
        JPanel card = createCard();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        mainPanel.add(card, gbc);
        setContentPane(mainPanel);
    }

    private JPanel createCard() {
        JPanel card = new RoundedPanel(20);
        card.setBackground(CARD_BG);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(50, 50, 50, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);
        gbc.anchor = GridBagConstraints.CENTER;

        // 타이틀
        JLabel title = new JLabel("멀티룸 채팅");
        title.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 36));
        title.setForeground(TEXT_PRIMARY);
        card.add(title, gbc);

        // 서브타이틀
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 40, 0);
        JLabel subtitle = new JLabel("네트워크 프로그래밍 프로젝트");
        subtitle.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
        subtitle.setForeground(TEXT_PRIMARY);
        card.add(subtitle, gbc);

        // 닉네임 입력
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 20, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        card.add(createInputGroup("닉네임", tfNickname = createTextField(), "BMDOHYEON_ttf.ttf"), gbc);

        // 서버 주소 입력
        gbc.gridy++;
        card.add(createInputGroup("서버 주소", tfHost = createTextField(), "BMDOHYEON_ttf.ttf"), gbc);

        // 포트 입력 (배민 도현체)
        gbc.gridy++;
        card.add(createInputGroup("포트", tfPort = createTextField(), "BMDOHYEON_ttf.ttf"), gbc);

        // 2. 기본값 설정 (Constants 사용)
        tfHost.setText(Constants.DEFAULT_HOST);
        tfPort.setText(String.valueOf(Constants.DEFAULT_PORT));

        // 시작 버튼
        gbc.gridy++;
        gbc.insets = new Insets(20, 0, 20, 0);
        btnStart = createStartButton();
        card.add(btnStart, gbc);

        // 하단 캡션
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        JLabel features = new JLabel("미니게임  |  이모티콘  |  비밀모드");
        features.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        features.setForeground(TEXT_PRIMARY);
        features.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(features, gbc);

        // 유효성 검사
        DocumentListener validator = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { validateForm(); }
            public void removeUpdate(DocumentEvent e) { validateForm(); }
            public void changedUpdate(DocumentEvent e) { validateForm(); }
        };
        tfNickname.getDocument().addDocumentListener(validator);
        tfHost.getDocument().addDocumentListener(validator);
        tfPort.getDocument().addDocumentListener(validator);

        return card;
    }

    private JPanel createInputGroup(String labelText, JTextField textField, String fontFile) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(350, 80));
        panel.setPreferredSize(new Dimension(350, 80));

        JLabel label = new JLabel(labelText);
        label.setFont(loadCustomFont(fontFile, Font.BOLD, 13));
        label.setForeground(TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));

        textField.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(label);
        panel.add(textField);

        return panel;
    }
    // 프로젝트 resources 폴더에서 커스텀 폰트 로드하기

    private Font loadCustomFont(String fontFileName, int style, int size) {
        try {
            String path = "fonts/ttf/" + fontFileName;
            InputStream fontStream = getClass().getClassLoader().getResourceAsStream(path);

            if (fontStream != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                Font derivedFont = baseFont.deriveFont(style, (float) size);
                fontStream.close();
                return derivedFont;
            }
        } catch (Exception e) {
            // 폰트 로드 실패 시 조용히 fallback
        }

        // 실패 시 시스템 폰트 반환
        return new Font("Dialog", style, size);
    }

    /**
     * 한국어를 지원하는 안전한 폰트를 반환 (Fallback용)
     */
    private Font getKoreanFont(int style, int size) {
        // 기본적으로 나눔고딕 시도
        Font font = new Font("NanumGothic", style, size);
        if (font.canDisplayUpTo("한글테스트") == -1) {
            return font;
        }
        return new Font("Dialog", style, size);
    }

    private JTextField createTextField() {
        JTextField tf = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque() && getBorder() instanceof RoundedBorder) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };

        tf.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 15)); // 배민 한나 Air (입력값)
        tf.setForeground(TEXT_PRIMARY);
        tf.setBackground(Color.WHITE);
        tf.setBorder(new RoundedBorder(10, INPUT_BORDER, INPUT_FOCUS));
        tf.setPreferredSize(new Dimension(350, 45));
        tf.setMaximumSize(new Dimension(350, 45));
        tf.setOpaque(false);

        return tf;
    }

    private JButton createStartButton() {
        JButton btn = new JButton("채팅 시작하기") {
            private boolean hover = false;

            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        if (isEnabled()) {
                            hover = true;
                            repaint();
                        }
                    }
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isEnabled()) {
                    g2.setColor(hover ? PRIMARY_HOVER : PRIMARY);
                } else {
                    g2.setColor(new Color(229, 231, 235)); // 밝은 회색 배경
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();

                super.paintComponent(g);
            }
        };

        btn.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.BOLD, 16)); // 배민 한나 Air (버튼)
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(350, 50));
        btn.setMaximumSize(new Dimension(350, 50));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setEnabled(false);
        btn.addActionListener(e -> connectAndOpenChat());

        // 활성화/비활성화 상태에 따라 글씨 색 변경
        btn.addPropertyChangeListener("enabled", evt -> {
            if ((Boolean) evt.getNewValue()) {
                btn.setForeground(Color.WHITE);
            } else {
                btn.setForeground(new Color(156, 163, 175)); // 비활성 시 어두운 회색
            }
        });

        // 초기 비활성화 상태 색상 설정
        btn.setForeground(new Color(156, 163, 175));

        return btn;
    }

    private void validateForm() {
        String nick = tfNickname.getText().trim();
        String host = tfHost.getText().trim();
        String port = tfPort.getText().trim();

        boolean valid = !nick.isEmpty() && !host.isEmpty();
        try {
            valid = valid && Integer.parseInt(port) > 0;
        } catch (Exception e) {
            valid = false;
        }

        btnStart.setEnabled(valid);
    }

    private void connectAndOpenChat() {
        String nick = tfNickname.getText().trim();
        String host = tfHost.getText().trim();
        int port = Integer.parseInt(tfPort.getText().trim());

        btnStart.setEnabled(false);
        btnStart.setText("연결 중...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            ChatClient client;
            Exception error;

            @Override
            protected Void doInBackground() {
                try {
                    client = new ChatClient();
                    client.connect(host, port);
                    client.sendMessage(nick);
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    btnStart.setEnabled(true);
                    btnStart.setText("채팅 시작하기");
                    JOptionPane.showMessageDialog(
                            LoginFrame.this,
                            "서버 연결 실패:\n" + error.getMessage(),
                            "오류",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                // 방 목록 화면으로 전환
                RoomListFrame rooms = new RoomListFrame(nick, host + ":" + port);
                rooms.bind(client);
                rooms.setVisible(true);
                dispose();
            }
        };
        worker.execute();
    }

    // ========== 커스텀 컴포넌트 ==========

    /**
     * 둥근 모서리 패널
     */
    static class RoundedPanel extends JPanel {
        private final int radius;

        RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
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

    /**
     * 둥근 모서리 테두리
     */
    static class RoundedBorder extends EmptyBorder {
        private final int radius;
        private final Color normalColor;
        private final Color focusColor;

        RoundedBorder(int radius, Color normalColor, Color focusColor) {
            super(12, 16, 12, 16);
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

    // ========== 메인 ==========

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            LoginFrame frame = new LoginFrame();
            frame.setVisible(true);
        });
    }
}