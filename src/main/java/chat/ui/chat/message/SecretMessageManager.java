package chat.ui.chat.message;

import chat.ui.common.UiUtils;
import chat.ui.common.Colors;
import chat.ui.fonts.FontManager;
import chat.shared.EmojiRegistry;
import chat.util.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 방 단위 시크릿 메시지 컨트롤러
 * - sid 발급/동기화 이벤트(@secret:on/off/msg/clear) 처리
 * - 배너/버튼/말풍선 동기화
 * - sid→컴포넌트 버킷 저장해서 clear 시 정확히 제거
 */

public class SecretMessageManager {
    public interface SecretUiDelegate {
        void onSecretTheme(boolean on);
    }

    private final int emojiSize;
    private static final Insets EMOJI_PAD = new Insets(10, 10, 10, 10); // 패딩 살짝 키움


    private final SecretUiDelegate uiDelegate;
    private final JPanel chatContainer;
    private final JScrollPane chatScroll;
    private final JToggleButton btnSecret;
    private final String myNick;


    private boolean secretOn = false;
    private String currentSid = null;
    private final Map<String, List<JComponent>> buckets = new HashMap<>();

    public SecretMessageManager(JPanel chatContainer, JScrollPane chatScroll,
                                JToggleButton btnSecret, String myNick, SecretUiDelegate uiDelegate, int emojiSize) {
        this.chatContainer = chatContainer;
        this.chatScroll = chatScroll;
        this.btnSecret = btnSecret;
        this.myNick = myNick;
        this.uiDelegate = uiDelegate;
        this.emojiSize = emojiSize;
    }
    /* ===== 서버 이벤트 핸들러 ===== */

    // @secret:on {sid} {hostNick}
    public void onSecretOn(String sid) {
        // 이미 같은 sid로 시크릿 모드면 배너 다시 안 붙이기
        if (secretOn && java.util.Objects.equals(currentSid, sid)) {
            return;
        }
        secretOn = true;
        currentSid = sid;

        SwingUtilities.invokeLater(() -> {
            btnSecret.setEnabled(false);   // 클릭 잠깐 비활성화
            btnSecret.setSelected(true);
            if (uiDelegate != null) uiDelegate.onSecretTheme(true);
            btnSecret.setEnabled(true);    // UI 반영 후 복구
        });
    }

    // @secret:off {sid} {hostNick}
    public void onSecretOff() {
        if (!secretOn) return;

        secretOn = false;
        final String sidToClear = currentSid;
        currentSid = null;

        // UI 작업은 EDT에서
        SwingUtilities.invokeLater(() -> {
            btnSecret.setEnabled(false);

            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                for (List<JComponent> list : buckets.values()) {
                    for (JComponent c : list) chatContainer.remove(c);
                }
                buckets.clear();
            });

            btnSecret.setSelected(false);
            if (uiDelegate != null) uiDelegate.onSecretTheme(false);
            btnSecret.setEnabled(true);
        });
    }
    public void onSecretMsg(String sid, String user, String msg) {
        SwingUtilities.invokeLater(() -> {
            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {

                if (user.equals(myNick)) {
                    return;
                }
                String code = null;

                if (msg.startsWith(Constants.PKG_EMOJI)) {
                    code = msg.substring(Constants.PKG_EMOJI.length()).trim();
                }
                else if (msg.matches("^:[a-z_]+:$")) {
                    code = msg;
                }

                JComponent panel;

                if (code != null && EmojiRegistry.findEmoji(code) != null) {
                    String path = EmojiRegistry.findEmoji(code);
                    ImageIcon icon = loadEmojiIcon(path);
                    if (icon != null) {
                        // 시크릿 이모티콘 말풍선
                        panel = buildLeftEmojiBubble(user, icon, true);
                    } else {
                        // 아이콘 실패 시 텍스트로라도 표시
                        panel = buildLeftBubble(user, code, true);
                    }
                } else {
                    // 일반 시크릿 텍스트
                    panel = buildLeftBubble(user, msg, true);
                }

                chatContainer.add(panel);
                chatContainer.add(Box.createVerticalStrut(8));
                buckets.computeIfAbsent(sid, k -> new ArrayList<>()).add(panel);
            });
        });
    }


    // @secret:clear {sid}
    public void onSecretClear(String sid) {
        SwingUtilities.invokeLater(() -> {
            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                List<JComponent> list = buckets.remove(sid);
                if (list != null) {
                    for (JComponent c : list) chatContainer.remove(c);
                }
            });
        });
    }

    /* ===== 로컬 UI 액션 ===== */

    public boolean isSecretOn() { return secretOn; }
    public String currentSid()  { return currentSid; }

    public void addMySecretEcho(String msg) {
        SwingUtilities.invokeLater(() -> {
            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
                messagePanel.setOpaque(false);
                messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

                JLabel timeLabel = new JLabel(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));
                timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
                timeLabel.setForeground(Colors.TEXT_SECONDARY);

                // 시크릿: 점선 버블
                JPanel bubble = createBubble(
                        msg,
                        Colors.SECRET_ACCENT,
                        Colors.TEXT_PRIMARY,
                        /*dashed*/ true
                );


                messagePanel.add(timeLabel);
                messagePanel.add(bubble);
                chatContainer.add(messagePanel);
                chatContainer.add(Box.createVerticalStrut(8));

                if (currentSid != null) {
                    buckets.computeIfAbsent(currentSid, k -> new ArrayList<>()).add(messagePanel);
                }
            });
        });
    }
    private JPanel buildLeftBubble(String user, String text, boolean secret) {
        JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        messagePanel.setOpaque(false);
        messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JLabel avatar = new JLabel(makeAvatarIcon(user));
        avatar.setPreferredSize(new Dimension(40, 40));

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(user);
        nameLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
        nameLabel.setForeground(Colors.TEXT_SECONDARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        row.setOpaque(false);

        boolean dashed = secret;
        Color bubbleColor = secret
                ? Colors.SECRET_ACCENT
                : Colors.OTHER_BUBBLE;
        Color textColor = Colors.TEXT_PRIMARY;

        JPanel bubble = createBubble(text, bubbleColor, textColor, dashed);

        JLabel timeLabel = new JLabel(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));
        timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
        timeLabel.setForeground(Colors.TEXT_SECONDARY);

        row.add(bubble);
        row.add(timeLabel);

        rightPanel.add(nameLabel);
        rightPanel.add(Box.createVerticalStrut(4));
        rightPanel.add(row);

        messagePanel.add(avatar);
        messagePanel.add(rightPanel);
        return messagePanel;
    }

    private JPanel createBubble(String text, Color baseColor, Color textColor, boolean dashed) {
        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (dashed) {
                    float[] dash = {6f, 4f};
                    g2.setColor(baseColor);
                    g2.setStroke(new BasicStroke(
                            2f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND,
                            10f,
                            dash,
                            0f
                    ));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 15, 15);
                } else {
                    g2.setColor(baseColor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                }

                g2.dispose();
                super.paintComponent(g);
            }
        };

        bubble.setLayout(new BorderLayout());
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel label = new JLabel("<html><body style='width: 300px'>" + text + "</body></html>");
        label.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        label.setForeground(textColor);
        bubble.add(label, BorderLayout.CENTER);

        return bubble;
    }


    private Icon makeAvatarIcon(String user) {
        return new Icon() {
            public int getIconWidth() { return 40; }
            public int getIconHeight() { return 40; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Colors.PRIMARY);
                g2.fillOval(x, y, 40, 40);
                g2.setColor(Color.WHITE);
                g2.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
                String initial = user.isEmpty() ? "?" : user.substring(0,1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(initial);
                int h = fm.getAscent();
                g2.drawString(initial, x + (40 - w)/2, y + (40 + h)/2 - 2);
                g2.dispose();
            }
        };
    }

    public void optimisticOn() {
        this.secretOn = true;
        if (uiDelegate != null) uiDelegate.onSecretTheme(true);
    }

    public void optimisticOff() {
        if (!secretOn) return;

        this.secretOn = false;
        if (uiDelegate != null) uiDelegate.onSecretTheme(false);

        final String sidToClear = currentSid;
        currentSid = null;

        if (sidToClear != null) {
            onSecretClear(sidToClear);
        }
    }

    private JPanel createSecretEmojiBubble(ImageIcon icon, boolean mine) {
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float[] dash = {6f, 4f};
                g2.setColor(Colors.SECRET_ACCENT);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(EMOJI_PAD));
        bubble.add(new JLabel(icon));
        return bubble;
    }

    private ImageIcon loadEmojiIcon(String path) {
        try {
            java.net.URL url = getClass().getClassLoader().getResource(path);
            if (url == null) return null;
            Image img = new ImageIcon(url).getImage()
                    .getScaledInstance(emojiSize, emojiSize, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        } catch (Exception e) {
            return null;
        }
    }


    private JPanel buildLeftEmojiBubble(String user, ImageIcon icon, boolean secret) {
        JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        messagePanel.setOpaque(false);

        JLabel avatar = new JLabel(makeAvatarIcon(user));
        avatar.setPreferredSize(new Dimension(40, 40));

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(user);
        nameLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
        nameLabel.setForeground(Colors.TEXT_SECONDARY);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        row.setOpaque(false);

        JPanel bubble = secret
                ? createSecretEmojiBubble(icon, false)
                : createBubble("", Colors.OTHER_BUBBLE, Colors.TEXT_PRIMARY, false); // 일반모드 필요시

        if (!secret) {
            bubble.removeAll();
            bubble.add(new JLabel(icon));
        }

        JLabel timeLabel = new JLabel(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));
        timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
        timeLabel.setForeground(Colors.TEXT_SECONDARY);

        row.add(bubble);
        row.add(timeLabel);

        rightPanel.add(nameLabel);
        rightPanel.add(Box.createVerticalStrut(4));
        rightPanel.add(row);

        messagePanel.add(avatar);
        messagePanel.add(rightPanel);

        return messagePanel;
    }

    public void addMySecretEmoji(String code) {
        String path = EmojiRegistry.findEmoji(code);
        if (path == null) {
            addMySecretEcho(code);
            return;
        }

        ImageIcon icon = loadEmojiIcon(path);
        if (icon == null) {
            addMySecretEcho(code);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
                panel.setOpaque(false);

                JLabel timeLabel = new JLabel(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));
                timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
                timeLabel.setForeground(Colors.TEXT_SECONDARY);

                JPanel bubble = createSecretEmojiBubble(icon, true);

                panel.add(timeLabel);
                panel.add(bubble);

                chatContainer.add(panel);
                chatContainer.add(Box.createVerticalStrut(8));

                if (currentSid != null) {
                    buckets.computeIfAbsent(currentSid, k -> new ArrayList<>()).add(panel);
                }
            });
        });
    }
    public String getCurrentSid() {
        return currentSid;
    }
}
