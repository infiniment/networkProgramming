package chat.ui.chat;

import chat.client.ChatClient;
import chat.util.Constants;

// 🔁 새로 분리한 공통/폰트 유틸 임포트
import chat.ui.common.Colors;
import chat.ui.common.RoundedPanel;
import chat.ui.common.RoundedBorder;
import chat.ui.common.UiUtils;
import chat.ui.fonts.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ChatFrame - 고급 채팅 화면
 * 이모티콘 패널, 폭탄 메시지 타이머, 말풍선 스타일, 미니게임 선택
 * - 색상/폰트/공통컴포넌트는 Colors, FontManager, RoundedPanel/Border, UiUtils 로 이관
 */
public class ChatFrame extends JFrame implements ChatClient.MessageListener {

    private final String nickname;
    private final String serverLabel;

    private ChatClient client;
    private JFrame parentFrame;
    private boolean shouldDisconnect = true;

    // UI 컴포넌트
    private JPanel chatContainer;
    private JScrollPane chatScroll;
    private JTextField tfInput;
    private JLabel lblStatusIcon;
    private JLabel lblStatusText;
    private JLabel lblTypingIndicator;
    private JLabel lblMembers;
    private JButton btnSend;
    private JToggleButton btnSecretMode;
    private JButton btnMiniGame;
    private JButton btnEmoticon;
    private JButton btnBombMessage;

    // 이모티콘 팝업
    private JWindow emoticonWindow;

    // 상태
    private boolean isSecretMode = false;
    private boolean typingOn = false;
    private javax.swing.Timer typingStopTimer;
    private static final int TYPING_START_DEBOUNCE_MS = 300;  // 시작 디바운스
    private static final int TYPING_STOP_DELAY_MS    = 1500; // 마지막 입력 후 STOP 지연
    private long lastTypingStartSentAt = 0L;

    // 시크릿 메시지 버킷 : sid -> 해당 sid로 렌더된 컴포넌트 목록
    private final Map<String, java.util.List<JComponent>> secretBuckets = new HashMap<>();
    private Set<String> typingUsers = new HashSet<>();

    // 이모티콘 목록
    private static final String[] EMOTICONS = {
            ":)", ":(", ";)", ":D", "XD", ":P",
            ":O", ":|", ":/", "<3", "B)", "8)",
            ">:(", ":*", ":'(", "^_^", "-_-", "O_O",
            "T_T", ">_<", "^^", "*_*", "@_@", "o_o"
    };

    // 🎮 게임 리스너/버퍼
    private List<ChatClient.MessageListener> gameListeners = new CopyOnWriteArrayList<>();
    private List<String> gameMessageBuffer = new CopyOnWriteArrayList<>();

    public ChatFrame(String nickname, String serverLabel, JFrame parentFrame) {
        this.nickname = nickname;
        this.serverLabel = serverLabel;
        this.parentFrame = parentFrame;

        setTitle("멀티룸 채팅 - " + serverLabel);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Colors.BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        mainPanel.add(buildHeader(), BorderLayout.NORTH);
        mainPanel.add(buildChatArea(), BorderLayout.CENTER);
        mainPanel.add(buildInputArea(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (shouldDisconnect && client != null) {
                    try {
                        client.sendMessage(Constants.CMD_QUIT);
                        client.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        // 🔤 폰트 사전 로딩(깜빡임/지연 방지)
        FontManager.preload();
    }

    // ========== 게임 리스너 관리 ==========
    public void addGameListener(ChatClient.MessageListener listener) {
        System.out.println("[ChatFrame] 🎮 게임 리스너 등록 시작: " +
                listener.getClass().getSimpleName());

        synchronized (gameListeners) {
            gameListeners.add(listener);
            System.out.println("[ChatFrame] ✅ 리스너 등록 완료 (총 " + gameListeners.size() + "개)");
        }

        // 버퍼 즉시 비우기
        System.out.println("[ChatFrame] 📊 버퍼된 게임 메시지 개수: " + gameMessageBuffer.size());
        if (!gameMessageBuffer.isEmpty()) {
            java.util.List<String> bufferCopy = new java.util.ArrayList<>(gameMessageBuffer);
            for (String msg : bufferCopy) {
                try {
                    listener.onMessageReceived(msg);
                } catch (Exception e) {
                    System.err.println("[ChatFrame] 전달 실패: " + e.getMessage());
                }
            }
            gameMessageBuffer.clear();
            System.out.println("[ChatFrame] 버퍼 전달 완료");
        }
    }

    public void removeGameListener(ChatClient.MessageListener listener) {
        gameListeners.remove(listener);
    }

    // ========== 헤더 영역 ==========
    private JComponent buildHeader() {
        JPanel header = new RoundedPanel(15);
        header.setBackground(Colors.CARD_BG);
        header.setBorder(new EmptyBorder(16, 20, 16, 20));
        header.setLayout(new BorderLayout(20, 0));
        header.setPreferredSize(new Dimension(0, 70));

        // 왼쪽 - 뒤로가기 + 방 정보
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setOpaque(false);

        JButton btnBack = new JButton("◀") {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    public void mouseExited (MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(hover ? Colors.INPUT_BORDER : Colors.INPUT_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setFont(new Font("Dialog", Font.BOLD, 18));
                g2.setColor(Colors.TEXT_PRIMARY);
                FontMetrics fm = g2.getFontMetrics();
                String text = "◀";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(text, x, y);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnBack.setPreferredSize(new Dimension(40, 40));
        btnBack.setFocusPainted(false);
        btnBack.setBorderPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBack.setOpaque(false);
        btnBack.addActionListener(e -> {
            shouldDisconnect = false;
            if (parentFrame != null) parentFrame.setVisible(true);
            dispose();
        });

        JPanel roomInfo = new JPanel(new GridLayout(2, 1, 0, 2));
        roomInfo.setOpaque(false);

        JLabel lblRoom = new JLabel(serverLabel);
        lblRoom.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 15));
        lblRoom.setForeground(Colors.TEXT_PRIMARY);

        lblMembers = new JLabel("참여자 0명");
        lblMembers.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        lblMembers.setForeground(Colors.TEXT_SECONDARY);

        roomInfo.add(lblRoom);
        roomInfo.add(lblMembers);

        leftPanel.add(btnBack);
        leftPanel.add(roomInfo);

        // 오른쪽 - 시크릿 모드 + 미니게임 + 상태 + 닉네임
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        btnSecretMode = createSecretModeButton();
        btnMiniGame = createMiniGameButton();

        lblStatusIcon = new JLabel(UiUtils.makeStatusIcon(Colors.PRIMARY));
        lblStatusText = new JLabel("연결");
        lblStatusText.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
        lblStatusText.setForeground(Colors.TEXT_SECONDARY);

        JLabel lblUser = new JLabel(nickname);
        lblUser.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 11));
        lblUser.setForeground(Colors.TEXT_PRIMARY);

        rightPanel.add(btnSecretMode);
        rightPanel.add(btnMiniGame);
        rightPanel.add(lblStatusIcon);
        rightPanel.add(lblStatusText);
        rightPanel.add(lblUser);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(header, BorderLayout.CENTER);
        wrapper.setBorder(new EmptyBorder(0, 0, 10, 0));
        return wrapper;
    }

    // ========== 채팅 영역 ==========
    private JComponent buildChatArea() {
        JPanel container = new RoundedPanel(15);
        container.setBackground(Colors.CARD_BG);
        container.setLayout(new BorderLayout());

        chatContainer = new JPanel();
        chatContainer.setLayout(new BoxLayout(chatContainer, BoxLayout.Y_AXIS));
        chatContainer.setBackground(Colors.BG_COLOR);
        chatContainer.setBorder(new EmptyBorder(16, 16, 16, 16));

        chatScroll = new JScrollPane(chatContainer);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatScroll.getViewport().setBackground(Colors.BG_COLOR);

        lblTypingIndicator = new JLabel(" ");
        lblTypingIndicator.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.ITALIC, 12));
        lblTypingIndicator.setForeground(Colors.TEXT_SECONDARY);
        lblTypingIndicator.setBorder(new EmptyBorder(8, 20, 8, 20));

        container.add(chatScroll, BorderLayout.CENTER);
        container.add(lblTypingIndicator, BorderLayout.SOUTH);

        return container;
    }

    // ========== 입력 영역 ==========
    private JComponent buildInputArea() {
        JPanel inputPanel = new RoundedPanel(15);
        inputPanel.setBackground(Colors.CARD_BG);
        inputPanel.setBorder(new EmptyBorder(16, 20, 16, 20));
        inputPanel.setLayout(new BorderLayout(12, 0));

        // 왼쪽 - 부가 기능 버튼들
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.setOpaque(false);

        // 이모티콘 버튼
        btnEmoticon = createIconButton("^_^");
        btnEmoticon.setToolTipText("이모티콘");
        btnEmoticon.addActionListener(e -> showEmoticonPanel());
        leftButtons.add(btnEmoticon);

        // 폭탄 메시지 버튼
        btnBombMessage = createIconButton("BOMB");
        btnBombMessage.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 10));
        btnBombMessage.setToolTipText("폭탄 메시지");
        btnBombMessage.addActionListener(e -> showBombMessageDialog());
        leftButtons.add(btnBombMessage);

        // 입력 필드
        tfInput = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        tfInput.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        tfInput.setForeground(Colors.TEXT_PRIMARY);
        tfInput.setBackground(Colors.INPUT_BG);
        tfInput.setBorder(new RoundedBorder(10, Colors.INPUT_BORDER, Colors.PRIMARY));
        tfInput.setPreferredSize(new Dimension(0, 45));
        tfInput.setOpaque(false);

        tfInput.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { sendTypingStatus(true); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { sendTypingStatus(true); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        tfInput.addActionListener(e -> { sendMessage(); tfInput.requestFocusInWindow(); });

        // 전송 버튼
        btnSend = createSendButton();
        btnSend.addActionListener(e -> sendMessage());

        JPanel inputWrapper = new JPanel(new BorderLayout(12, 0));
        inputWrapper.setOpaque(false);
        inputWrapper.add(leftButtons, BorderLayout.WEST);
        inputWrapper.add(tfInput, BorderLayout.CENTER);

        inputPanel.add(inputWrapper, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);

        return inputPanel;
    }

    // ========== 이모티콘 패널 ==========
    private void showEmoticonPanel() {
        if (emoticonWindow != null && emoticonWindow.isVisible()) {
            emoticonWindow.setVisible(false);
            return;
        }

        emoticonWindow = new JWindow(this);
        emoticonWindow.setAlwaysOnTop(true);

        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Colors.INPUT_BORDER, 2),
                new EmptyBorder(12, 12, 12, 12)
        ));
        panel.setLayout(new GridLayout(0, 6, 8, 8));

        for (String emoticon : EMOTICONS) {
            JButton btn = new JButton(emoticon) {
                private boolean hover = false;
                {
                    addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                        public void mouseExited (MouseEvent e) { hover = false; repaint(); }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(hover ? Colors.INPUT_BORDER : Colors.INPUT_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
            btn.setForeground(Colors.TEXT_PRIMARY);
            btn.setPreferredSize(new Dimension(50, 50));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setOpaque(false);

            btn.addActionListener(e -> {
                tfInput.setText(tfInput.getText() + " " + emoticon);
                tfInput.requestFocus();
                emoticonWindow.setVisible(false);
            });

            panel.add(btn);
        }

        emoticonWindow.add(panel);
        emoticonWindow.pack();

        Point loc = btnEmoticon.getLocationOnScreen();
        emoticonWindow.setLocation(loc.x, loc.y - emoticonWindow.getHeight() - 5);
        emoticonWindow.setVisible(true);

        emoticonWindow.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { emoticonWindow.setVisible(false); }
        });
    }

    // ========== 폭탄 메시지 다이얼로그 ==========
//    private void showBombMessageDialog() {
//        JDialog dialog = new JDialog(this, "폭탄 메시지", true);
//        dialog.setLayout(new BorderLayout(10, 10));
//        dialog.setSize(350, 250);
//        dialog.setLocationRelativeTo(this);
//
//        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
//        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
//        mainPanel.setBackground(Color.WHITE);
//
//        JLabel title = new JLabel("전달하세요", JLabel.CENTER);
//        title.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
//        title.setForeground(Colors.TEXT_PRIMARY);
//
//        JPanel timerPanel = new JPanel(new BorderLayout(10, 10));
//        timerPanel.setOpaque(false);
//
//        JLabel timerLabel = new JLabel("자동삭제 시간");
//        timerLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
//
//        JComboBox<String> timerCombo = new JComboBox<>(new String[]{"10초", "30초", "1분", "5분"});
//        timerCombo.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
//
//        timerPanel.add(timerLabel, BorderLayout.WEST);
//        timerPanel.add(timerCombo, BorderLayout.CENTER);
//
//        JTextArea messageArea = new JTextArea(3, 20);
//        messageArea.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
//        messageArea.setLineWrap(true);
//        messageArea.setWrapStyleWord(true);
//        messageArea.setBorder(BorderFactory.createCompoundBorder(
//                BorderFactory.createLineBorder(Colors.INPUT_BORDER, 1),
//                new EmptyBorder(8, 8, 8, 8)
//        ));
//        JScrollPane scrollPane = new JScrollPane(messageArea);
//
//        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
//        buttonPanel.setOpaque(false);
//
//        JButton btnCancel = createDialogButton("취소", new Color(149, 165, 166));
//        btnCancel.addActionListener(e -> dialog.dispose());
//
//        JButton btnSend = createDialogButton("전송", Colors.PRIMARY);
//        btnSend.addActionListener(e -> {
//            String msg = messageArea.getText().trim();
//            if (!msg.isEmpty()) {
//                int seconds = getSecondsFromCombo((String) timerCombo.getSelectedItem());
//                sendBombMessage(msg, seconds);
//                dialog.dispose();
//            }
//        });
//
//        buttonPanel.add(btnCancel);
//        buttonPanel.add(btnSend);
//
//        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
//        centerPanel.setOpaque(false);
//        centerPanel.add(timerPanel, BorderLayout.NORTH);
//        centerPanel.add(scrollPane, BorderLayout.CENTER);
//
//        mainPanel.add(title, BorderLayout.NORTH);
//        mainPanel.add(centerPanel, BorderLayout.CENTER);
//        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
//
//        dialog.add(mainPanel);
//        dialog.setVisible(true);
//    }

    private void showBombMessageDialog() {
        JDialog dialog = new JDialog(this, "폭탄 메시지", Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(350, 250);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        JLabel title = new JLabel("전달하세요", JLabel.CENTER);
        title.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        title.setForeground(Colors.TEXT_PRIMARY);

        JPanel timerPanel = new JPanel(new BorderLayout(10, 10));
        timerPanel.setOpaque(false);

        JLabel timerLabel = new JLabel("자동삭제 시간");
        timerLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));

        JComboBox<String> timerCombo = new JComboBox<>(new String[]{"10초", "30초", "1분", "5분"});
        timerCombo.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));

        timerPanel.add(timerLabel, BorderLayout.WEST);
        timerPanel.add(timerCombo, BorderLayout.CENTER);

        JTextArea messageArea = new JTextArea(3, 20);
        messageArea.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Colors.INPUT_BORDER, 1),
                new EmptyBorder(8, 8, 8, 8)
        ));
        JScrollPane scrollPane = new JScrollPane(messageArea);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        JButton btnCancel = createDialogButton("취소", new Color(149, 165, 166));
        btnCancel.addActionListener(e -> dialog.dispose());

        JButton btnSend = createDialogButton("전송", Colors.PRIMARY);
        btnSend.addActionListener(e -> {
            String msg = messageArea.getText().trim();
            if (!msg.isEmpty()) {
                int seconds = switch ((String) timerCombo.getSelectedItem()) {
                    case "10초" -> 10;
                    case "30초" -> 30;
                    case "1분"  -> 60;
                    case "잉"   -> 300; // 오타 방지용, 기본값 아래에서 처리
                    default      -> 300; // "5분"
                };
                sendBombMessage(msg, seconds);
                dialog.dispose();
            }
        });

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnSend);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);
        centerPanel.add(timerPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(title, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }


    // ========== 게임 선택 모달 ==========
    private void showGameSelectionDialog() {
//        JDialog dialog = new JDialog(this, "게임 선택", true);
//        dialog.setLayout(new BorderLayout(10, 10));
//        dialog.setSize(535, 320);
//        dialog.setLocationRelativeTo(this);
//        dialog.setResizable(false);
        // 문서 모달(해당 프레임만 블록)
        JDialog dialog = new JDialog(this, "게임 선택", false);
        dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL); // 또는 MODELESS
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(535, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        JLabel title = new JLabel("게임 선택");
        title.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 18));
        title.setForeground(Colors.TEXT_PRIMARY);

        JLabel subtitle = new JLabel("채팅방에서 함께 즐길 게임을 선택하세요");
        subtitle.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        subtitle.setForeground(Colors.TEXT_SECONDARY);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 5));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(subtitle);

        JPanel gamePanel = new JPanel(new GridLayout(1, 2, 16, 0));
        gamePanel.setOpaque(false);

        JPanel omokCard = createGameCard("game1.png", "오목", "2인용 • 오목 게임");
        omokCard.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { selectGame("omok"); dialog.dispose(); }
        });

        JPanel br31Card = createGameCard("BRbaskinrobbins.png", "베스킨라빈스31", "다인용 • 베스킨라빈스31");
        br31Card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { selectGame("br31"); dialog.dispose(); }
        });

        gamePanel.add(omokCard);
        gamePanel.add(br31Card);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(false);

        JButton btnCancel = createDialogButton("취소", new Color(149, 165, 166));
        btnCancel.addActionListener(e -> dialog.dispose());
        buttonPanel.add(btnCancel);

        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(gamePanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private JPanel createGameCard(String imagePath, String gameName, String description) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(new Color(252, 245, 235));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 240, 240), 1),
                new EmptyBorder(20, 20, 20, 20)
        ));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(245, 235, 220));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Colors.INPUT_BORDER, 2),
                        new EmptyBorder(20, 20, 20, 20)
                ));
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(new Color(252, 245, 235));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(240, 240, 240), 1),
                        new EmptyBorder(20, 20, 20, 20)
                ));
            }
        });

        JLabel imageLabel = new JLabel();
        ImageIcon icon = loadGameImage(imagePath);
        if (icon != null) {
            Image scaled = icon.getImage().getScaledInstance(70, 70, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaled));
        } else {
            imageLabel.setText(imagePath.contains("game1") ? "🟡" : "📊");
            imageLabel.setFont(new Font("Dialog", Font.PLAIN, 48));
        }
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(0, 70));

        JLabel nameLabel = new JLabel(gameName);
        nameLabel.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        nameLabel.setForeground(Colors.TEXT_PRIMARY);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel descLabel = new JLabel("<html><body style='text-align: center;'>" + description + "</body></html>");
        descLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        descLabel.setForeground(Colors.TEXT_SECONDARY);
        descLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(imageLabel, BorderLayout.NORTH);
        card.add(nameLabel, BorderLayout.CENTER);
        card.add(descLabel, BorderLayout.SOUTH);

        return card;
    }

    private void selectGame(String gameType) {
        if (client == null) {
            JOptionPane.showMessageDialog(this, "서버 연결이 끊어졌습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (gameType.equals("omok")) {
            OmokGameFrame omokGame = new OmokGameFrame(nickname, client, this);
            omokGame.setAlwaysOnTop(true);
            omokGame.requestFocus();
            omokGame.setVisible(true);
            addSystemMessage("🎮 " + nickname + "님이 미니게임에 참여하였습니다.");
        } else if (gameType.equals("br31")) {
            client.sendMessage(Constants.CMD_31);
            addSystemMessage("🎮 베스킨라빈스31 게임을 시작했습니다!");
        }
    }

    private int getSecondsFromCombo(String selected) {
        return switch (selected) {
            case "10초" -> 10;
            case "30초" -> 30;
            case "1분" -> 60;
            case "5분" -> 300;
            default -> 10;
        };
    }

    // ========== 다이얼로그 버튼 ==========
    private JButton createDialogButton(String text, Color color) {
        JButton btn = new JButton(text) {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { if (isEnabled()) { hover = true; repaint(); } }
                    public void mouseExited (MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? color.darker() : color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(80, 35));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);
        return btn;
    }

    // ========== 시크릿 모드 버튼 ==========
    private JToggleButton createSecretModeButton() {
        JToggleButton btn = new JToggleButton("SECRET") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(isSelected() ? new Color(231, 76, 60) : new Color(149, 165, 166));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 9));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String text = "SECRET";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(text, x, y);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setPreferredSize(new Dimension(65, 30));
        btn.setMinimumSize(new Dimension(65, 30));
        btn.setMaximumSize(new Dimension(65, 30));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> {
            isSecretMode = btn.isSelected();
            if (isSecretMode) {
                showSecretModeNotice();
                if (client != null) client.sendMessage(Constants.CMD_SECRET_ON);
            } else {
                if (client != null) client.sendMessage(Constants.CMD_SECRET_OFF);
            }
        });
        return btn;
    }

    // ========== 미니게임 버튼 ==========
    private JButton createMiniGameButton() {
        JButton btn = new JButton("🎮") {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { if (isEnabled()) { hover = true; repaint(); } }
                    public void mouseExited (MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(hover ? Colors.INPUT_BORDER : new Color(230, 230, 230));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setFont(new Font("Dialog", Font.PLAIN, 16));
                g2.setColor(Colors.TEXT_PRIMARY);
                FontMetrics fm = g2.getFontMetrics();
                String text = "🎮";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(text, x, y);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setPreferredSize(new Dimension(40, 30));
        btn.setMinimumSize(new Dimension(40, 30));
        btn.setMaximumSize(new Dimension(40, 30));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);
        btn.setToolTipText("미니게임 선택");
//        btn.addActionListener(e -> showGameSelectionDialog());
        btn.addActionListener(e -> { System.out.println("[UI] miniGame clicked by " + nickname); showGameSelectionDialog(); });
        return btn;
    }

    // ========== 아이콘 버튼 ==========
    private JButton createIconButton(String text) {
        JButton btn = new JButton(text) {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    public void mouseExited (MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? Colors.INPUT_BORDER : Colors.INPUT_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        btn.setForeground(Colors.TEXT_PRIMARY);
        btn.setPreferredSize(new Dimension(50, 45));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);
        return btn;
    }

    // ========== 전송 버튼 ==========
    private JButton createSendButton() {
        JButton btn = new JButton("전송") {
            private boolean hover = false;
            private boolean pressed = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered (MouseEvent e) { if (isEnabled()) { hover = true; repaint(); } }
                    public void mouseExited  (MouseEvent e) { hover = false; pressed = false; repaint(); }
                    public void mousePressed (MouseEvent e) { if (isEnabled()) { pressed = true; repaint(); } }
                    public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (pressed)      g2.setColor(new Color(255, 120, 20));
                else if (hover)   g2.setColor(Colors.PRIMARY_HOVER);
                else              g2.setColor(Colors.PRIMARY);
                int offsetY = pressed ? 2 : 0;
                g2.translate(0, offsetY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - (pressed ? 2 : 0), 10, 10);
                g2.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String text = "전송";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(text, x, y);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setPreferredSize(new Dimension(80, 45));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);
        return btn;
    }

    // ========== 메시지 전송 ==========
//    private void sendMessage() {
//        String msg = tfInput.getText().trim();
//        if (msg.isEmpty() || client == null) return;
//
//        if (msg.matches("^:[a-z_]+:$")) {
//            String packet = Constants.PKG_EMOJI + " " + msg;
//            client.sendMessage(packet);
//            System.out.println("[CLIENT] 이모티콘 전송: " + packet);
//        } else if (msg.matches("^[a-z_]+$") && msg.contains("_")) {
//            String packet = Constants.PKG_STICKER + " " + msg;
//            client.sendMessage(packet);
//            System.out.println("[CLIENT] 스티커 전송: " + packet);
//        } else {
//            client.sendMessage(msg);
//        }
//
//        addMyMessage(msg, isSecretMode);
//        tfInput.setText("");
//        sendTypingStatus(false);
//    }
    private void sendMessage() {
        String msg = tfInput.getText().trim();
        if (msg.isEmpty() || client == null) return;

        // /bomb 가 입력창에서 직접 들어온 경우: 로컬 버블 + 서버엔 명령만 보냄
        if (msg.startsWith("/bomb ")) {
            client.sendMessage(msg);          // 서버로 원본 명령 전송
            tfInput.setText("");
            sendTypingStatus(false);
            return;
        }

        // 모든 슬래시 명령어는 채팅 버블을 만들지 않음
        if (msg.startsWith("/")) {
            client.sendMessage(msg);
            tfInput.setText("");
            sendTypingStatus(false);
            return;
        }

        // 이모티콘/스티커 패킷은 그대로 전송
        if (msg.matches("^:[a-z_]+:$")) {
            String packet = Constants.PKG_EMOJI + " " + msg;
            client.sendMessage(packet);
        } else if (msg.matches("^[a-z_]+$") && msg.contains("_")) {
            String packet = Constants.PKG_STICKER + " " + msg;
            client.sendMessage(packet);
        } else {
            client.sendMessage(msg);
        }

        // 일반 채팅만 내 버블 생성
        addMyMessage(msg, isSecretMode);
        tfInput.setText("");
        sendTypingStatus(false);
    }

    private void sendBombMessage(String msg, int seconds) {
        if (client == null) return;
        client.sendMessage(Constants.CMD_BOMB + " " + seconds + " " + msg);
    }

    // ========== 타이핑 상태 ==========
    private void sendTypingStatus(boolean typing) {
        if (client == null) return;

        long now = System.currentTimeMillis();
        if (typing) {
            if (!typingOn && (now - lastTypingStartSentAt) >= TYPING_START_DEBOUNCE_MS) {
                client.sendMessage(Constants.CMD_TYPING_START);
                typingOn = true;
                lastTypingStartSentAt = now;
            }
            if (typingStopTimer != null && typingStopTimer.isRunning()) typingStopTimer.stop();
            typingStopTimer = new javax.swing.Timer(TYPING_STOP_DELAY_MS, e -> {
                if (typingOn && client != null) client.sendMessage(Constants.CMD_TYPING_STOP);
                typingOn = false;
            });
            typingStopTimer.setRepeats(false);
            typingStopTimer.start();
        } else {
            if (typingStopTimer != null && typingStopTimer.isRunning()) typingStopTimer.stop();
            if (typingOn) client.sendMessage(Constants.CMD_TYPING_STOP);
            typingOn = false;
        }
    }

    private void updateTypingIndicator() {
        SwingUtilities.invokeLater(() -> {
            if (typingUsers.isEmpty()) {
                lblTypingIndicator.setText(" ");
            } else if (typingUsers.size() == 1) {
                String user = typingUsers.iterator().next();
                lblTypingIndicator.setText("> " + user + "님이 입력 중입니다...");
            } else {
                lblTypingIndicator.setText("> " + typingUsers.size() + "명이 입력 중입니다...");
            }
        });
    }

    // ========== 시크릿 모드 알림 ==========
    private void showSecretModeNotice() {
        JPanel notice = new JPanel(new FlowLayout(FlowLayout.CENTER));
        notice.setOpaque(false);
        notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel label = new JLabel("[!] 시크릿 모드 활성화 - 메시지가 저장되지 않습니다");
        label.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
        label.setForeground(new Color(231, 76, 60));

        notice.add(label);
        chatContainer.add(notice);
        chatContainer.add(Box.createVerticalStrut(8));
    }

    // ========== 메시지 말풍선 출력 ==========
    private void addMyMessage(String text, boolean isSecret) {
        UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
            JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
            messagePanel.setOpaque(false);
            messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

            JLabel timeLabel = new JLabel(getCurrentTime());
            timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            timeLabel.setForeground(Colors.TEXT_SECONDARY);

            JPanel bubble = createBubble(text, isSecret ? new Color(231, 76, 60) : Colors.MY_BUBBLE, Color.WHITE);

            messagePanel.add(timeLabel);
            messagePanel.add(bubble);

            chatContainer.add(messagePanel);
            chatContainer.add(Box.createVerticalStrut(8));
        });
    }

    private void addBombMessage(String text, int seconds) {
        final JPanel[] holder = new JPanel[1];
        UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
            JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
            holder[0] = messagePanel;
            messagePanel.setOpaque(false);
            messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

            JLabel timeLabel = new JLabel(getCurrentTime());
            timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            timeLabel.setForeground(Colors.TEXT_SECONDARY);

            JPanel bubble = createBubble(text, new Color(155, 89, 182), Color.WHITE);

            messagePanel.add(timeLabel);
            messagePanel.add(bubble);

            chatContainer.add(messagePanel);
            chatContainer.add(Box.createVerticalStrut(8));
        });

        new Timer().schedule(new TimerTask() {
            @Override public void run() {
                UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                    if (holder[0] != null) chatContainer.remove(holder[0]);
                });
            }
        }, seconds * 1000L);
    }

    private void addOtherMessage(String user, String text) {
        UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
            JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            messagePanel.setOpaque(false);
            messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

            JLabel avatar = new JLabel(getAvatarIcon(user));
            avatar.setPreferredSize(new Dimension(40, 40));

            JPanel rightPanel = new JPanel();
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
            rightPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(user);
            nameLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
            nameLabel.setForeground(Colors.TEXT_SECONDARY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel bubbleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            bubbleRow.setOpaque(false);

            JPanel bubble = createBubble(text, Colors.OTHER_BUBBLE, Colors.TEXT_PRIMARY);
            JLabel timeLabel = new JLabel(getCurrentTime());
            timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            timeLabel.setForeground(Colors.TEXT_SECONDARY);

            bubbleRow.add(bubble);
            bubbleRow.add(timeLabel);

            rightPanel.add(nameLabel);
            rightPanel.add(Box.createVerticalStrut(4));
            rightPanel.add(bubbleRow);

            messagePanel.add(avatar);
            messagePanel.add(rightPanel);

            chatContainer.add(messagePanel);
            chatContainer.add(Box.createVerticalStrut(8));
        });
    }

    private JPanel createBubble(String text, Color bgColor, Color textColor) {
        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
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

    private Icon getAvatarIcon(String user) {
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
                String initial = user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(initial);
                int h = fm.getAscent();
                g2.drawString(initial, x + (40 - w) / 2, y + (40 + h) / 2 - 2);
                g2.dispose();
            }
        };
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    // ========== 시스템 메시지 ==========
    public void addSystemMessage(String message) {
        addOtherMessage("System", message);
    }

    // 참여자 수 업데이트
    public void updateMemberCount(int count) {
        SwingUtilities.invokeLater(() -> lblMembers.setText("참여자 " + count + "명"));
    }

    // ========== ChatClient 바인딩 ==========
    public void bind(ChatClient client) {
        this.client = client;
        this.client.startReceiving(this);
        tfInput.requestFocus();
    }

    public void addBufferedLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        for (String line : lines) onMessageReceived(line);
    }

    @Override
    public void onMessageReceived(String line) {
        // 방 목록 응답은 채팅창에 표시하지 않음
        if (line.startsWith(Constants.RESPONSE_ROOMS + " ") || line.startsWith("@rooms ")) return;

        // 폭탄 이벤트
        if (line.startsWith(Constants.EVT_BOMB)) {
            // 형식: "@bomb {sec} {nick}: {msg}"
            String rest = line.substring(Constants.EVT_BOMB.length()).trim();
            int sp = rest.indexOf(' ');
            if (sp > 0) {
                int sec = 5;
                try { sec = Integer.parseInt(rest.substring(0, sp)); } catch (Exception ignored) {}
                String payload = rest.substring(sp + 1).trim(); // "{nick}: {msg}"
                String user = extractUsername(payload);
                String msg  = extractMessage(payload);
                if (user == null || msg == null) return;

                if (user.equals(nickname)) {
                    // 나의 폭탄(오른쪽 말풍선)
                    addBombMessage(msg, sec);
                } else {
                    // 상대 폭탄(왼쪽 말풍선) – 자동삭제 포함
                    addOtherBombMessage(user, msg, sec);
                }
            }
            return;
        }

        // 게임 선택 모달 트리거 (신규 규격)
        if (line.startsWith("@game:menu")) {
            SwingUtilities.invokeLater(this::showGameSelectionDialog);
            return;
        }
        // 하위호환: 예전 서버가 "[GAME]"을 보낸 경우도 모달 오픈
        if (line.startsWith("[GAME]")) {
            SwingUtilities.invokeLater(this::showGameSelectionDialog);
            return;
        }
        // (혹시 구버전 서버에서 "@rooms "로 보낸다면 아래도 함께)
        if (line.startsWith("@rooms ")) return;
        System.out.println("[ChatFrame] 📥 onMessageReceived: " + line + " / listeners=" + gameListeners.size());
        if (line.startsWith("@game:")) {
            handleGameMessage(line);
            return;
        }
        parseAndDisplayMessage(line);
    }

    private void addOtherBombMessage(String user, String text, int seconds) {
        final JPanel[] holder = new JPanel[1];
        UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
            JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            holder[0] = messagePanel;
            messagePanel.setOpaque(false);
            messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

            JLabel avatar = new JLabel(getAvatarIcon(user));
            avatar.setPreferredSize(new Dimension(40, 40));

            JPanel right = new JPanel();
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            right.setOpaque(false);

            JLabel name = new JLabel(user);
            name.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
            name.setForeground(Colors.TEXT_SECONDARY);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            row.setOpaque(false);

            JPanel bubble = createBubble(text, new Color(155, 89, 182), Color.WHITE);
            JLabel time = new JLabel(getCurrentTime());
            time.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            time.setForeground(Colors.TEXT_SECONDARY);

            row.add(bubble);
            row.add(time);

            right.add(name);
            right.add(Box.createVerticalStrut(4));
            right.add(row);

            messagePanel.add(avatar);
            messagePanel.add(right);

            chatContainer.add(messagePanel);
            chatContainer.add(Box.createVerticalStrut(8));
        });

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                    if (holder[0] != null) chatContainer.remove(holder[0]);
                });
            }
        }, seconds * 1000L);
    }


    private void handleGameMessage(String line) {
        synchronized (gameListeners) {
            if (gameListeners.isEmpty()) {
                gameMessageBuffer.add(line);
                return;
            }
            for (ChatClient.MessageListener listener : gameListeners) {
                try { listener.onMessageReceived(line); } catch (Exception ignored) {}
            }
        }
    }

    private void parseAndDisplayMessage(String line) {
        if (line == null) return;
        line = line.trim();

        if (!line.contains(":") && !line.startsWith(Constants.EVT_SECRET_MSG)) {
            addSystemMessage(line);
            return;
        }

        if (line.startsWith("[BOMB ")) {
            if (line.contains("] " + nickname + ":")) return; // 내 폭탄은 무시해서 중복 제거
        }

        // 시크릿 메시지
        if (line.startsWith(Constants.EVT_SECRET_MSG)) {
            String rest = line.substring(Constants.EVT_SECRET_MSG.length()).trim();
            int sp = rest.indexOf(' ');
            if (sp > 0) {
                String sid = rest.substring(0, sp);
                String payload = rest.substring(sp + 1);
                String user = extractUsername(payload);
                String msg  = extractMessage(payload);
                addOtherMessageSecret(user, msg, sid);
            }
            return;
        }

        // 시크릿 클리어
        if (line.startsWith(Constants.EVT_SECRET_CLEAR)) {
            String sid = line.substring(Constants.EVT_SECRET_CLEAR.length()).trim();
            UiUtils.commitChatUpdate(chatContainer, chatScroll, () -> {
                java.util.List<JComponent> list = secretBuckets.remove(sid);
                if (list != null) for (JComponent comp : list) chatContainer.remove(comp);
            });
            return;
        }

        // 자신의 메시지 무시
        if (line.startsWith(nickname + ":")) return;

        // 시스템 메시지
        if (line.startsWith("[System] ")) {
            String message = line.substring("[System] ".length()).trim();
            if (message.contains("비밀 채팅 모드 ON"))  isSecretMode = true;
            if (message.contains("비밀 채팅 모드 OFF")) isSecretMode = false;
            addSystemMessage(message);
            return;
        }

        // 타이핑 상태
        if (line.contains(Constants.CMD_TYPING_START) || line.contains(Constants.CMD_TYPING_STOP)) {
            String status = line.contains(Constants.CMD_TYPING_START) ? Constants.CMD_TYPING_START : Constants.CMD_TYPING_STOP;
            String user = extractUsername(line);
            if (status.equals(Constants.CMD_TYPING_START)) typingUsers.add(user);
            else typingUsers.remove(user);
            updateTypingIndicator();
            return;
        }

        // 일반 메시지
        String user = extractUsername(line);
        String message = extractMessage(line);
        if (user != null && message != null) addOtherMessage(user, message);
    }

    private String extractUsername(String line) {
        int idx = line.indexOf(":");
        if (idx > 0) return line.substring(0, idx).trim();
        return "Unknown";
    }

    private String extractMessage(String line) {
        int idx = line.indexOf(":");
        if (idx > 0 && idx < line.length() - 1) return line.substring(idx + 1).trim();
        return line;
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            lblStatusIcon.setIcon(UiUtils.makeStatusIcon(Color.RED));
            lblStatusText.setText("연결 끊김");
            btnSend.setEnabled(false);
            tfInput.setEnabled(false);
        });
    }

    private void addOtherMessageSecret(String user, String text, String sid) {
        SwingUtilities.invokeLater(() -> {
            JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            messagePanel.setOpaque(false);
            messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

            JLabel avatar = new JLabel(getAvatarIcon(user));
            avatar.setPreferredSize(new Dimension(40, 40));

            JPanel rightPanel = new JPanel();
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
            rightPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(user);
            nameLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
            nameLabel.setForeground(Colors.TEXT_SECONDARY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel bubbleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            bubbleRow.setOpaque(false);

            JPanel bubble = createBubble(text, new Color(231, 76, 60), Color.WHITE);
            JLabel timeLabel = new JLabel(getCurrentTime());
            timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            timeLabel.setForeground(Colors.TEXT_SECONDARY);

            bubbleRow.add(bubble);
            bubbleRow.add(timeLabel);

            rightPanel.add(nameLabel);
            rightPanel.add(Box.createVerticalStrut(4));
            rightPanel.add(bubbleRow);

            messagePanel.add(avatar);
            messagePanel.add(rightPanel);

            chatContainer.add(messagePanel);
            chatContainer.add(Box.createVerticalStrut(8));

            secretBuckets.computeIfAbsent(sid, k -> new ArrayList<>()).add(messagePanel);
        });
    }

    // 이미지 로드
    private ImageIcon loadGameImage(String filename) {
        try {
            String path = "images/" + filename;
            InputStream imageStream = getClass().getClassLoader().getResourceAsStream(path);
            if (imageStream != null) {
                byte[] data = imageStream.readAllBytes();
                imageStream.close();
                return new ImageIcon(data);
            }
        } catch (Exception e) {
            System.err.println("이미지 로드 실패: " + filename);
        }
        return null;
    }
}
