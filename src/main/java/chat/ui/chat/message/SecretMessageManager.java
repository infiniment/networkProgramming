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

    private final SecretUiDelegate uiDelegate;
    private final JPanel chatContainer;
    private final JScrollPane chatScroll;
    private final JToggleButton btnSecret;
    private final String myNick;


    private boolean secretOn = false;
    private String currentSid = null; // 서버가 브로드캐스트한 sid
    private final Map<String, List<JComponent>> buckets = new HashMap<>();

    public SecretMessageManager(JPanel chatContainer, JScrollPane chatScroll,
                                JToggleButton btnSecret, String myNick, SecretUiDelegate uiDelegate) {
        this.chatContainer = chatContainer;
        this.chatScroll = chatScroll;
        this.btnSecret = btnSecret;
        this.myNick = myNick;
        this.uiDelegate = uiDelegate;
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

//        new Thread(() -> {  // 🧵 별도 스레드로 clear 실행
//            SwingUtilities.invokeLater(() -> {
//                btnSecret.setEnabled(false);
//                UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
//                    for (List<JComponent> list : buckets.values())
//                        for (JComponent c : list)
//                            chatContainer.remove(c);
//                    buckets.clear();
//                });
//                btnSecret.setSelected(false);
//                if (uiDelegate != null) uiDelegate.onSecretTheme(false);
//                btnSecret.setEnabled(true);
//            });
//        }).start();
        // UI 작업은 EDT에서
        SwingUtilities.invokeLater(() -> {
            btnSecret.setEnabled(false);

            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                // 현재 구현: 시크릿 끌 때 해당 sid 관계없이 모든 시크릿 버킷 제거
                // (방 단위 시크릿이라 sid가 1개씩이므로 이게 더 안전)
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

//    public void onSecretOff() {
//        if (!secretOn) return; // 이미 꺼져있으면 무시
//        final String sidToClear = currentSid;
//
//        secretOn = false;
//        currentSid = null;
//
//        SwingUtilities.invokeLater(() -> {
//            btnSecret.setSelected(false);
//            if (uiDelegate != null) uiDelegate.onSecretTheme(false);
//
//            // 이 타이밍에 clear 실행
//            if (sidToClear != null) {
//                UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
//                    List<JComponent> list = buckets.remove(sidToClear);
//                    if (list != null) for (JComponent c : list) chatContainer.remove(c);
//                });
//            }
//        });
//    }

    // @secret:msg {sid} {nick}: {msg}
//    public void onSecretMsg(String sid, String user, String msg) {
//        SwingUtilities.invokeLater(() -> {
//            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
//                boolean isMyMessage = user.equals(myNick);
//
//                // 왼쪽 버블 + 비밀 모드 표시 (점선)
//                JPanel panel = buildLeftBubble(
//                        isMyMessage ? user + " (나)" : user,
//                        msg,
//                        /*secret*/ true
//                );
//
//                chatContainer.add(panel);
//                chatContainer.add(Box.createVerticalStrut(8));
//                buckets.computeIfAbsent(sid, k -> new ArrayList<>()).add(panel);
//            });
//        });
//    }

    public void onSecretMsg(String sid, String user, String msg) {
        SwingUtilities.invokeLater(() -> {
            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {

                // 내 메시지는 ChatFrame에서 이미 로컬 렌더했으니 보통은 넘어오지 않지만,
                // 혹시라도 오면 중복 방지
                if (user.equals(myNick)) {
                    return;
                }

                // ---- 이모티콘인지 판별 ----
                String code = null;

                // 형식 1) "@PKG_EMOJI :code:"
                if (msg.startsWith(Constants.PKG_EMOJI)) {
                    code = msg.substring(Constants.PKG_EMOJI.length()).trim();
                }
                // 형식 2) ":code:"만 온 경우(호환)
                else if (msg.matches("^:[a-z_]+:$")) {
                    code = msg;
                }

                JComponent panel;

                if (code != null && EmojiRegistry.findEmoji(code) != null) {
                    String path = EmojiRegistry.findEmoji(code);
                    ImageIcon icon = loadEmojiIcon(path);
                    if (icon != null) {
                        // 🔒 시크릿 이모티콘 말풍선 (점선)
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

    /** 내가 보낸 시크릿 메시지는 로컬에서 말풍선을 만들지 않는다(서버 에코만 렌더). */
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
                        Colors.SECRET_ACCENT,           // 점선 색
                        Colors.TEXT_PRIMARY,
                        /*dashed*/ true
                );


                messagePanel.add(timeLabel);
                messagePanel.add(bubble);
                chatContainer.add(messagePanel);
                chatContainer.add(Box.createVerticalStrut(8));

                // 버킷에 등록 (내 메시지도 clear 대상)
                if (currentSid != null) {
                    buckets.computeIfAbsent(currentSid, k -> new ArrayList<>()).add(messagePanel);
                }
            });
        });
    }

    /* ===== 내부 UI 유틸 ===== */

//    private void addBanner() {
//        UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
//            JPanel notice = new JPanel(new FlowLayout(FlowLayout.CENTER));
//            notice.setOpaque(false);
//            notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
//
//            JLabel label = new JLabel("[!] 시크릿 모드 활성화 - 메시지가 저장되지 않습니다");
//            label.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
//            label.setForeground(Colors.SECRET_ACCENT);
//
//            notice.add(label);
//            chatContainer.add(notice);
//            chatContainer.add(Box.createVerticalStrut(8));
//        });
//    }


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
                ? Colors.SECRET_ACCENT        // 점선 색
                : Colors.OTHER_BUBBLE;        // 일반 버블 배경
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

    /**
     * dashed == false : 채워진 일반 말풍선
     * dashed == true  : 투명 배경 + 점선 테두리
     */
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

//    public void optimisticOff() {
//        this.secretOn = false;
//        if (uiDelegate != null) uiDelegate.onSecretTheme(false);
//
//        // 내 화면에서도 시크릿 끄면 즉시 메시지 제거
//        if (currentSid != null) {
//            onSecretClear(currentSid);
//            currentSid = null;
//        }
//    }

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
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 시크릿은 점선 테두리
                float[] dash = {6f, 4f};
                g2.setColor(Colors.SECRET_ACCENT);
                g2.setStroke(new BasicStroke(
                        2f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10f,
                        dash,
                        0f
                ));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 15, 15);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(6, 6, 6, 6));
        bubble.add(new JLabel(icon));
        return bubble;
    }

    private ImageIcon loadEmojiIcon(String path) {
        try {
            java.net.URL url = getClass().getClassLoader().getResource(path);
            if (url == null) return null;
            Image img = new ImageIcon(url).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
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
