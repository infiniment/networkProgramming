    package chat.ui.chat;

    import chat.client.ChatClient;
    import chat.ui.chat.panels.EmojiPickerPanel;
    import chat.ui.common.*;
    import chat.util.Constants;
    import chat.ui.chat.message.SecretMessageManager;

    import chat.shared.EmojiRegistry;
    import java.util.Map;

    // 새로 분리한 공통/폰트 유틸 임포트
    import chat.ui.fonts.FontManager;

    import javax.swing.*;
    import javax.swing.border.EmptyBorder;
    import java.awt.*;
    import java.awt.event.*;
    import java.io.InputStream;
    import java.text.SimpleDateFormat;
    import java.util.*;
    import java.util.List;
    import java.util.concurrent.CopyOnWriteArrayList;

    /**
     * ChatFrame - 고급 채팅 화면
     * 이모티콘 패널, 폭탄 메시지 타이머, 말풍선 스타일, 미니게임 선택
     * - 색상/폰트/공통컴포넌트는 Colors, FontManager, RoundedPanel/Border, UiUtils 로 이관
     */
    public class ChatFrame extends JFrame implements ChatClient.MessageListener {

        private final String nickname;
        private final String serverLabel;

        private static final int EMOJI_CHAT_SIZE   = 96; // 채팅에 찍히는 이모티콘 크기(px)
        private static final int EMOJI_PICKER_SIZE = 56; // 이모티콘 선택창 썸네일 크기(px)
        private static final int BOMB_ICON_SIZE    = 28; // 폭탄 말풍선 안의 폭탄 아이콘 크기(px)

        private ImageIcon bombIcon;
        private ChatClient client;
        private JFrame parentFrame;
        private boolean shouldDisconnect = true;

        private SecretMessageManager secretMgr;

        private JPanel mainPanel;     // 전체 컨테이너
        private JComponent headerCard;    // 헤더 카드
        private JComponent chatCard;      // 채팅 카드(스크롤 감싸는 상단 컨테이너)
        private JComponent inputCard;     // 입력 카드
        private JLabel lblRoom;
        private JLabel lblUser;


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
        private JPanel emoticonPanel;

        // 상태
        private boolean typingOn = false;
        private javax.swing.Timer typingStopTimer;
        private static final int TYPING_START_DEBOUNCE_MS = 300;  // 시작 디바운스
        private static final int TYPING_STOP_DELAY_MS    = 1500; // 마지막 입력 후 STOP 지연
        private long lastTypingStartSentAt = 0L;

        // 시크릿 메시지 버킷 : sid -> 해당 sid로 렌더된 컴포넌트 목록
        private Set<String> typingUsers = new HashSet<>();


        // 🎮 게임 리스너/버퍼
        private List<ChatClient.MessageListener> gameListeners = new CopyOnWriteArrayList<>();
        private List<String> gameMessageBuffer = new CopyOnWriteArrayList<>();

        public ChatFrame(String nickname, String serverLabel, JFrame parentFrame) {
            this.nickname = nickname;
            this.serverLabel = serverLabel;
            this.parentFrame = parentFrame;

            setTitle("멀티룸 채팅 - " + serverLabel);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            setSize(900, 700);
            setLocationRelativeTo(null);

            this.mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(Colors.BG_COLOR);
            mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));



            headerCard =  buildHeader();   // ← 반환값 참조 저장하도록 아래 buildHeader 수정
            chatCard   =  buildChatArea(); // ← 반환값 참조
            inputCard  =  buildInputArea();// ← 반환값 참조

            mainPanel.add(headerCard, BorderLayout.NORTH);
            mainPanel.add(chatCard, BorderLayout.CENTER);
            mainPanel.add(inputCard, BorderLayout.SOUTH);
            setContentPane(mainPanel);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {  // windowClosed → windowClosing으로 변경
//                    // X 버튼을 누르면 뒤로가기와 동일하게 처리
//                    shouldDisconnect = false;
//                    if (parentFrame != null) {
//                        parentFrame.setVisible(true);
//                    }
//                    dispose();
                    // X 눌렀을 때도 방 나가기 + 목록으로
                    leaveRoomAndBackToList();
                }

                @Override
                public void windowClosed(WindowEvent e) {
                    // 실제로 창이 닫힌 후 연결 종료 여부 확인
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
            secretMgr = new SecretMessageManager(chatContainer, chatScroll, btnSecretMode, nickname, on -> applySecretTheme(on), EMOJI_CHAT_SIZE);
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
            lblRoom = new JLabel(serverLabel);

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
//            btnBack.addActionListener(e -> {
//                shouldDisconnect = false;
//                if (parentFrame != null) parentFrame.setVisible(true);
//                dispose();
//            });
            btnBack.addActionListener(e -> leaveRoomAndBackToList());

            JPanel roomInfo = new JPanel(new GridLayout(2, 1, 0, 2));
            roomInfo.setOpaque(false);

            this.lblRoom = new JLabel(serverLabel);
            lblRoom.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 15));
            lblRoom.setForeground(Colors.TEXT_PRIMARY);

            lblMembers = new JLabel("참여자 0명");
            lblMembers.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
            lblMembers.setForeground(Colors.TEXT_SECONDARY);

            roomInfo.add(lblRoom);
            roomInfo.add(lblMembers);

            leftPanel.add(btnBack);
            leftPanel.add(roomInfo);

            JButton btnExit = new JButton() {  // ← 텍스트 제거
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
                    g2.setColor(hover ? new Color(230, 126, 34) : new Color(243, 156, 18));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 11));
                    g2.setColor(Color.WHITE);
                    FontMetrics fm = g2.getFontMetrics();
                    String text = "나가기";
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = (getHeight() + fm.getAscent()) / 2 - 2;
                    g2.drawString(text, x, y);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btnExit.setText(null);  // ← 기본 텍스트 제거
            btnExit.setPreferredSize(new Dimension(60, 26));
            btnExit.setFocusPainted(false);
            btnExit.setBorderPainted(false);
            btnExit.setContentAreaFilled(false);
            btnExit.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnExit.setOpaque(false);
            btnExit.addActionListener(e -> leaveRoomAndBackToList());

            // 오른쪽 - 시크릿 모드 + 미니게임 + 상태 + 닉네임
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightPanel.setOpaque(false);

            btnSecretMode = createSecretModeButton();
            btnMiniGame = createMiniGameButton();

            lblStatusIcon = new JLabel(UiUtils.makeStatusIcon(Colors.PRIMARY));
            lblStatusText = new JLabel("연결");
            lblStatusText.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            lblStatusText.setForeground(Colors.TEXT_SECONDARY);

            JLabel user = new JLabel(nickname);
            this.lblUser = user;
            lblUser.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 11));
            lblUser.setForeground(Colors.TEXT_PRIMARY);

            rightPanel.add(btnSecretMode);
            rightPanel.add(btnMiniGame);
            rightPanel.add(lblStatusIcon);
            rightPanel.add(lblStatusText);
            rightPanel.add(lblUser);
            rightPanel.add(btnExit);

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

            // 이모티콘 버튼 - 이미지로 변경
            // 이모티콘 버튼 - 이미지 + 테두리
            btnEmoticon = new JButton() {
                private boolean hover = false;
                {
                    addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) { hover = true; setCursor(new Cursor(Cursor.HAND_CURSOR)); repaint(); }
                        public void mouseExited (MouseEvent e) { hover = false; setCursor(new Cursor(Cursor.DEFAULT_CURSOR)); repaint(); }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // 배경 (호버 시 살짝 어둡게)
                    if (hover) {
                        g2.setColor(new Color(245, 245, 245));
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);
                    }

                    // 테두리
                    g2.setColor(hover ? Colors.PRIMARY : Colors.INPUT_BORDER);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);

                    // 이미지 로드 및 그리기
                    ImageIcon icon = loadImageIcon("images/emoji.png", 35);
                    if (icon != null) {
                        int x = (getWidth() - icon.getIconWidth()) / 2;
                        int y = (getHeight() - icon.getIconHeight()) / 2;
                        g2.drawImage(icon.getImage(), x, y, this);
                    }

                    g2.dispose();
                }
            };
            btnEmoticon.setPreferredSize(new Dimension(50, 45));
            btnEmoticon.setFocusPainted(false);
            btnEmoticon.setBorderPainted(false);
            btnEmoticon.setContentAreaFilled(false);
            btnEmoticon.setOpaque(false);
            btnEmoticon.setToolTipText("이모티콘");
            btnEmoticon.addActionListener(e -> openEmojiPicker());
            leftButtons.add(btnEmoticon);

// 폭탄 메시지 버튼 - 이미지 + 테두리
            btnBombMessage = new JButton() {
                private boolean hover = false;
                {
                    addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) { hover = true; setCursor(new Cursor(Cursor.HAND_CURSOR)); repaint(); }
                        public void mouseExited (MouseEvent e) { hover = false; setCursor(new Cursor(Cursor.DEFAULT_CURSOR)); repaint(); }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // 배경 (호버 시 살짝 어둡게)
                    if (hover) {
                        g2.setColor(new Color(245, 245, 245));
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);
                    }

                    // 테두리
                    g2.setColor(hover ? Colors.PRIMARY : Colors.INPUT_BORDER);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);

                    // 이미지 로드 및 그리기
                    ImageIcon icon = loadImageIcon("images/bombs.png", 35);
                    if (icon != null) {
                        int x = (getWidth() - icon.getIconWidth()) / 2;
                        int y = (getHeight() - icon.getIconHeight()) / 2;
                        g2.drawImage(icon.getImage(), x, y, this);
                    }

                    g2.dispose();
                }
            };
            btnBombMessage.setPreferredSize(new Dimension(50, 45));
            btnBombMessage.setFocusPainted(false);
            btnBombMessage.setBorderPainted(false);
            btnBombMessage.setContentAreaFilled(false);
            btnBombMessage.setOpaque(false);
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

        private ImageIcon loadImageIcon(String path, int size) {
            try {
                java.net.URL url = getClass().getClassLoader().getResource(path);
                if (url == null) {
                    System.err.println("이미지를 찾을 수 없습니다: " + path);
                    return null;
                }
                ImageIcon original = new ImageIcon(url);
                Image scaled = original.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            } catch (Exception e) {
                System.err.println("이미지 로드 실패: " + path + " - " + e.getMessage());
                return null;
            }
        }

        // ========== 이모티콘 패널 ==========
        private void toggleEmoticonPanel() {
            if (emoticonPanel == null) return;
            boolean visible = !emoticonPanel.isVisible();
            emoticonPanel.setVisible(visible);

            // 높이 재계산 & 리페인트
            inputCard.revalidate();
            inputCard.repaint();
        }

        private JPanel buildEmoticonPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);

            // 탭
            JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
            tabs.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Colors.INPUT_BORDER, 1),
                    new EmptyBorder(6, 6, 6, 6)
            ));

            // EmojiRegistry.categories(): Map<String, List<String>>
            for (Map.Entry<String, java.util.List<String>> e : EmojiRegistry.categories().entrySet()) {
                String cat = e.getKey();
                java.util.List<String> codes = e.getValue();

                JPanel grid = new JPanel(new GridLayout(0, 6, 8, 8));
                grid.setOpaque(false);
                for (String code : codes) {
                    String path = EmojiRegistry.findEmoji(code);
                    if (path == null) continue;
                    JButton b = new JButton(loadEmojiIconScaled(path, 40)); // 40~48 추천
                    b.setFocusPainted(false);
                    b.setContentAreaFilled(false);
                    b.setBorderPainted(false);
                    b.setToolTipText(code);
                    b.addActionListener(ev -> {
                        if (secretMgr != null && secretMgr.isSecretOn()) secretMgr.addMySecretEmoji(code);
                        else addMyEmojiMessage(code);
                        sendAsync(Constants.PKG_EMOJI + " " + code);
                        // 선택 후 자동 닫기 원하면:
                        emoticonPanel.setVisible(false);
                        inputCard.revalidate(); inputCard.repaint();
                        tfInput.requestFocusInWindow();
                    });
                    grid.add(b);
                }
                JScrollPane sp = new JScrollPane(grid,
                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                tabs.addTab(cat, sp);
            }

            panel.add(tabs, BorderLayout.CENTER);
            return panel;
        }

        // 보조: 스케일 버전
        private ImageIcon loadEmojiIconScaled(String path, int size) {
            java.net.URL url = getClass().getClassLoader().getResource(path);
            if (url == null) return null;
            Image img = new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        }


        // 경로의 이미지를 원하는 크기로 스케일해서 가져오기
        private ImageIcon loadIconScaled(String path, int size) {
            java.net.URL url = getClass().getClassLoader().getResource(path);
            if (url == null) return null;
            Image img = new ImageIcon(url).getImage()
                    .getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        }

        // 이모티콘 전용 헬퍼
        private ImageIcon loadEmojiIcon(String path, int size) {
            return loadIconScaled(path, size);
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
                String m = messageArea.getText().trim();
                if (!m.isEmpty()) {
                    int seconds = getSecondsFromCombo((String) timerCombo.getSelectedItem());
                    sendBombMessage(m, seconds);
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
                addSystemMessage("🎮 " + nickname + "님이 오목 게임에 참여하였습니다.");
            } else if (gameType.equals("br31")) {
                // ✅ BR31 게임 창 열기
                Br31GameFrame br31Game = new Br31GameFrame(nickname, client, this);
                br31Game.setAlwaysOnTop(true);
                br31Game.requestFocus();
                br31Game.setVisible(true);
                addSystemMessage("🎮 " + nickname + "님이 베스킨라빈스31 게임에 참여하였습니다.");
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
            JToggleButton btn = new JToggleButton() {  // ← 텍스트 제거
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
            btn.setText(null);  // ← 기본 텍스트 제거
            btn.setPreferredSize(new Dimension(65, 30));
            btn.setMinimumSize(new Dimension(65, 30));
            btn.setMaximumSize(new Dimension(65, 30));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btn.addActionListener(e -> {
                if (client == null) return;
                boolean wantOn = btn.isSelected();

                btn.setEnabled(false);

                if (wantOn) {
                    // 시크릿 ON
                    secretMgr.optimisticOn();
                    sendAsync(Constants.CMD_SECRET_ON);
                } else {
                    // 시크릿 OFF (중복 clear 금지!)
                    String sid = secretMgr.getCurrentSid();
                    secretMgr.optimisticOff();          // 내부에서 clear 실행됨
                    sendAsync(Constants.CMD_SECRET_OFF);

                }

                // 버튼 활성화 및 포커스 복원은 마지막에 한 번만
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(true);
                    tfInput.requestFocusInWindow();
                });
            });

            return btn;
        }

        // ========== 미니게임 버튼 ==========
        private JButton createMiniGameButton() {
            JButton btn = new JButton() {  // ← 텍스트 제거
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
            btn.setText(null);  // ← 기본 텍스트 제거
            btn.setPreferredSize(new Dimension(40, 30));
            btn.setMinimumSize(new Dimension(40, 30));
            btn.setMaximumSize(new Dimension(40, 30));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setOpaque(false);
            btn.setToolTipText("미니게임 선택");
            btn.addActionListener(e -> { System.out.println("[UI] miniGame clicked by " + nickname); showGameSelectionDialog(); });
            return btn;
        }

        // ========== 아이콘 버튼 ==========
        private JButton createIconButton(String text) {
            JButton btn = new JButton() {  // ← 생성자에서 텍스트 제거
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

                    // ✅ 텍스트 그리기 추가
                    g2.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
                    g2.setColor(Colors.TEXT_PRIMARY);
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = (getHeight() + fm.getAscent()) / 2 - 2;
                    g2.drawString(text, x, y);

                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setText(null);  // ← 기본 텍스트 제거
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
            JButton btn = new JButton() {  // ← 텍스트 제거
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
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
            btn.setText(null);  // ← 기본 텍스트 제거
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
//        private void sendMessage() {
//            String msg = tfInput.getText().trim();
//            if (msg.isEmpty() || client == null) return;
//
//            // 슬래시 커맨드는 버블 만들지 않음
//            if (msg.startsWith("/")) {
//                sendAsync(msg);
//                tfInput.setText("");
//                sendTypingStatus(false);
//                return;
//            }
//
//            boolean secretOn = secretMgr != null && secretMgr.isSecretOn();
//            boolean isEmoji = msg.matches("^:[a-z_]+:$") && EmojiRegistry.findEmoji(msg) != null;
//
//            String toSend = msg;
//            // :code: 형태 + 레지스트리 존재 → 이모티콘 패킷
//            if (isEmoji) {
//                toSend = Constants.PKG_EMOJI + " " + msg;
//            }
//
//            // === 로컬 렌더링 ===
//            if (secretOn) {
//                if (isEmoji) {
//                    secretMgr.addMySecretEmoji(msg);   // 시크릿 이모지
//                } else {
//                    secretMgr.addMySecretEcho(msg);    // 시크릿 텍스트
//                }
//            } else {
//                if (isEmoji) {
//                    addMyEmojiMessage(msg);            // 일반 이모지
//                } else {
//                    addMyMessage(msg, false);          // 일반 텍스트
//                }
//            }
//
//            // 2) 전송은 백그라운드로
//            final String payload = toSend;
//            sendAsync(payload);
//
//            tfInput.setText("");
//            sendTypingStatus(false);
//        }

        private void sendMessage() {
            String msg = tfInput.getText().trim();
            if (msg.isEmpty() || client == null) return;

            // 슬래시 커맨드는 버블 만들지 않음
            if (msg.startsWith("/")) {
                sendAsync(msg);
                tfInput.setText("");
                sendTypingStatus(false);
                return;
            }

            boolean secretOn = secretMgr != null && secretMgr.isSecretOn();
            boolean isEmoji = msg.matches("^:[a-z_]+:$") && EmojiRegistry.findEmoji(msg) != null;

            String toSend = msg;
            // :code: 형태 + 레지스트리 존재 → 이모티콘 패킷
            if (isEmoji) {
                toSend = Constants.PKG_EMOJI + " " + msg;
            }

            // === 로컬 렌더링 ===
            // 시크릿 모드일 때만 로컬에서 바로 그리기
            if (secretOn) {
                if (isEmoji) {
                    secretMgr.addMySecretEmoji(msg);   // 시크릿 이모지
                } else {
                    secretMgr.addMySecretEcho(msg);    // 시크릿 텍스트
                }
            }

            // 2) 전송은 백그라운드로
            final String payload = toSend;
            sendAsync(payload);

            tfInput.setText("");
            sendTypingStatus(false);
        }

        private void sendBombMessage(String msg, int seconds) {
            if (client == null) return;
    //        client.sendMessage(Constants.CMD_BOMB + " " + seconds + " " + msg);
            sendAsync(Constants.CMD_BOMB + " " + seconds + " " + msg);
        }

        // ========== 타이핑 상태 ==========
        private void sendTypingStatus(boolean typing) {
            if (client == null) return;

            long now = System.currentTimeMillis();
            if (typing) {
                if (!typingOn && (now - lastTypingStartSentAt) >= TYPING_START_DEBOUNCE_MS) {
    //                client.sendMessage(Constants.CMD_TYPING_START);
                    sendAsync(Constants.CMD_TYPING_START);
                    typingOn = true;
                    lastTypingStartSentAt = now;
                }
                if (typingStopTimer != null && typingStopTimer.isRunning()) typingStopTimer.stop();
                typingStopTimer = new javax.swing.Timer(TYPING_STOP_DELAY_MS, e -> {
                    if (typingOn && client != null) {
                        sendAsync(Constants.CMD_TYPING_STOP);
                    }
    //                    client.sendMessage(Constants.CMD_TYPING_STOP);
                    typingOn = false;
                });
                typingStopTimer.setRepeats(false);
                typingStopTimer.start();
            } else {
                if (typingStopTimer != null && typingStopTimer.isRunning()) typingStopTimer.stop();
                if (typingOn) {
    //                client.sendMessage(Constants.CMD_TYPING_STOP);
                    sendAsync(Constants.CMD_TYPING_STOP);
                }
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
            commitChat(() -> {
                JPanel notice = new JPanel(new FlowLayout(FlowLayout.CENTER));
                notice.setOpaque(false);
                notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                JLabel label = new JLabel("[!] 시크릿 모드 활성화 - 메시지가 저장되지 않습니다");
                label.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
                label.setForeground(new Color(231, 76, 60));

                notice.add(label);
                chatContainer.add(notice);
                chatContainer.add(Box.createVerticalStrut(8));
            });
        }

        // ========== 메시지 말풍선 출력 ==========
        private void addMyMessage(String text, boolean isSecret) {
            commitChat(() -> {
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

            commitChat(() -> {
                JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
                holder[0] = messagePanel;
                messagePanel.setOpaque(false);
                messagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

                JLabel timeLabel = new JLabel(getCurrentTime());
                timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
                timeLabel.setForeground(Colors.TEXT_SECONDARY);

                // 🔥 폭탄 아이콘 + 메시지가 들어간 버블
                JPanel bubble = createBombBubble(text, /*mine*/ true);

                messagePanel.add(timeLabel);
                messagePanel.add(bubble);

                chatContainer.add(messagePanel);
                chatContainer.add(Box.createVerticalStrut(8));
            });

            // 자동 삭제 타이머
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override public void run() {
                    commitChat(() -> {
                        if (holder[0] != null) chatContainer.remove(holder[0]);
                    });
                }
            }, seconds * 1000L);
        }


        private void addOtherMessage(String user, String text) {
            commitChat( () -> {
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

        private JPanel createBombBubble(String text, boolean mine) {
            Color bgColor = mine ? new Color(155, 89, 182) : new Color(142, 68, 173);

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
            bubble.setOpaque(false);
            bubble.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
            bubble.setBorder(new EmptyBorder(6, 10, 6, 10));

            // 아이콘 (없으면 💣 텍스트로 대체)
            ImageIcon icon = getBombIcon();
            if (icon != null) {
                JLabel iconLabel = new JLabel(icon);
                bubble.add(iconLabel);
            } else {
                JLabel fallback = new JLabel("💣");
                fallback.setFont(new Font("Dialog", Font.PLAIN, 14));
                bubble.add(fallback);
            }

            // 메시지 텍스트
            JLabel msgLabel = new JLabel("<html><body style='width: 260px'>" + text + "</body></html>");
            msgLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
            msgLabel.setForeground(Color.WHITE);
            bubble.add(msgLabel);

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
            SwingUtilities.invokeLater(() -> {
                lblStatusIcon.setIcon(UiUtils.makeStatusIcon(Colors.PRIMARY));
                lblStatusText.setText("연결");
                btnSend.setEnabled(true);
                tfInput.setEnabled(true);
                tfInput.requestFocusInWindow();

                // ✅ 방 입장 후 참여자 정보 자동 요청
                if (this.client != null) {
                    // 약간의 딜레이 후 요청 (서버 연결 안정화)
                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // 0.5초 대기
                            this.client.sendMessage("/who");
                        } catch (InterruptedException ignored) {}
                    }).start();
                }
            });
        }

        public void addBufferedLines(List<String> lines) {
            if (lines == null || lines.isEmpty()) return;
            for (String line : lines) onMessageReceived(line);
        }

        @Override
        public void onMessageReceived(String line) {
            // 방 목록 응답은 채팅창에 표시하지 않음
            if (line.startsWith(Constants.RESPONSE_ROOMS + " ") || line.startsWith("@rooms ")) return;

            // ✅ 참여자 정보 업데이트 추가 (여기!)
            if (line.startsWith("[System] 참여자 (")) {
                try {
                    // "[System] 참여자 (3): nick1, nick2, nick3" 형식에서 숫자 추출
                    int start = line.indexOf("(") + 1;
                    int end = line.indexOf(")");
                    if (start > 0 && end > start) {
                        String countStr = line.substring(start, end).trim();
                        int count = Integer.parseInt(countStr);
                        updateMemberCount(count);
                    }
                } catch (Exception e) {
                    System.err.println("참여자 수 파싱 실패: " + e.getMessage());
                }
                // 시스템 메시지로도 표시
                addSystemMessage(line.substring("[System] ".length()));
                return;
            }

            // 폭탄 이벤트
            if (line.startsWith(Constants.EVT_BOMB + " ")) {
                // 서버 포맷: "@bomb {sec} {nick}: {msg}"
                String payload = line.substring((Constants.EVT_BOMB + " ").length()).trim();

                // sec 추출
                int sp = payload.indexOf(' ');
                int sec = 5;
                String rest = payload;
                if (sp > 0) {
                    try { sec = Integer.parseInt(payload.substring(0, sp)); } catch (Exception ignored) {}
                    rest = payload.substring(sp + 1).trim();
                }

                // {nick}: {msg} 분리
                String nick = extractUsername(rest);
                String msg  = extractMessage(rest);
                if (nick == null || msg == null) return;

                // UI 갱신은 내부에서 commitChat으로 처리됨
                if (nick.equals(this.nickname)) {
                    addBombMessage(msg, sec);            // 내 메시지(오른쪽)
                } else {
                    addOtherBombMessage(nick, msg, sec); // 상대 메시지(왼쪽)
                }
                return;
            }

            // @secret:on {sid} {hostNick}
            if (line.startsWith(chat.util.Constants.EVT_SECRET_ON)) {
                String[] sp = line.split("\\s+", 3);
                String sid = sp.length > 1 ? sp[1] : null;
                String hostNick = sp.length > 2 ? sp[2] : null;

                System.out.printf("[DEBUG] secret:on sid=%s host=%s myNick=%s%n", sid, hostNick, nickname);
                secretMgr.onSecretOn(sid);
                return;
            }

            // @secret:off {sid} {hostNick}
            if (line.startsWith(chat.util.Constants.EVT_SECRET_OFF)) {
                String[] sp = line.split("\\s+", 3);
                String sid = sp.length > 1 ? sp[1] : null;
                String hostNick = sp.length > 2 ? sp[2] : null;

                System.out.printf("[DEBUG] secret:off sid=%s host=%s myNick=%s%n", sid, hostNick, nickname);
                secretMgr.onSecretOff();
                return;
            }

            // @secret:clear {sid}
            if (line.startsWith(chat.util.Constants.EVT_SECRET_CLEAR)) {
                String sid = line.substring(chat.util.Constants.EVT_SECRET_CLEAR.length()).trim();
                secretMgr.onSecretClear(sid);
                return;
            }

            // @secret:msg {sid} {nick}: {msg}
            if (line.startsWith(chat.util.Constants.EVT_SECRET_MSG)) {
                String rest = line.substring(chat.util.Constants.EVT_SECRET_MSG.length()).trim();
                int sp = rest.indexOf(' ');
                if (sp > 0) {
                    String sid = rest.substring(0, sp);
                    String payload = rest.substring(sp + 1).trim(); // "{nick}: {msg}"
                    String user = extractUsername(payload);
                    String msg  = extractMessage(payload);
                    if (user != null && msg != null) {
                        if (user.equals(nickname)) {
                            // 이미 로컬에서 렌더했으므로 에코는 무시 (중복 방지)
                            return;
                        } else {
                            secretMgr.onSecretMsg(sid, user, msg);
                        }
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
            commitChat(() -> {
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

                // 폭탄 아이콘 + 메시지가 들어간 버블
                JPanel bubble = createBombBubble(text, /*mine*/ false);


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
                    commitChat(() -> {
                        if (holder[0] != null) chatContainer.remove(holder[0]);
                    });
                }
            }, seconds * 1000L);
        }

        private void handleGameMessage(String line) {
            if (gameListeners.isEmpty()) {
                gameMessageBuffer.add(line);
                return;
            }
            for (ChatClient.MessageListener listener : gameListeners) {
                try { listener.onMessageReceived(line); } catch (Exception ignored) {}
            }
        }

        private void parseAndDisplayMessage(String line) {
            if (line == null) return;
            line = line.trim();
            if (line.isEmpty()) return;

            // 0) 이모티콘 JSON / 스티커 JSON / 패키지 헤더 등은 전부 무시
            if (line.startsWith("{")
                    || line.startsWith("1,{")
                    || line.startsWith("1,\"")
                    || line.contains("\"type\":\"emoji\"")
                    || line.contains("\"type\":\"sticker\"")
                    || line.startsWith(Constants.PKG_EMOJI)    // "@PKG_EMOJI ..."
                    || line.startsWith(Constants.PKG_STICKER)  // "@PKG_STICKER ..."
                    || line.startsWith("[EMOJI]")
                    || line.startsWith("[STICKER]")) {
                return;
            }

            // 1) 시스템 메시지
            if (line.startsWith("[System] ")) {
                String message = line.substring("[System] ".length()).trim();
                addSystemMessage(message);
                return;
            }

            // 2) 타이핑 상태
            if (line.contains(Constants.CMD_TYPING_START) || line.contains(Constants.CMD_TYPING_STOP)) {
                String status = line.contains(Constants.CMD_TYPING_START)
                        ? Constants.CMD_TYPING_START
                        : Constants.CMD_TYPING_STOP;

                String user = extractUsername(line);
                if (!"Unknown".equals(user)) {
                    if (status.equals(Constants.CMD_TYPING_START)) typingUsers.add(user);
                    else typingUsers.remove(user);
                    updateTypingIndicator();
                }
                return;
            }

            // 3) "닉네임: 내용" 구조 아닌 건 그냥 시스템성 문장으로 처리
            int idx = line.indexOf(':');
            if (idx <= 0) {
                addSystemMessage(line);
                return;
            }

            String user = line.substring(0, idx).trim();
            String payload = line.substring(idx + 1).trim(); // 내용 부분

            // 4) 이모티콘 메시지 (형식: "nick: @PKG_EMOJI :code:")
            if (payload.startsWith(Constants.PKG_EMOJI)) {
                String code = payload.substring(Constants.PKG_EMOJI.length()).trim(); // ":doing:" 같은 코드

                if (user.equals(nickname)) {
                    // 내 이모티콘
                    addMyEmojiMessage(code);
                } else {
                    // 다른 사람 이모티콘
                    addOtherEmojiMessage(user, code);
                }
                return;
            }

            // 5) 일반 텍스트 메시지
            if (payload.isEmpty()) {
                return;
            }

            if (user.equals(nickname)) {
                // 여기서 과거에 내가 보낸 메시지도 다시 그려줌
                addMyMessage(payload, false);
            } else {
                addOtherMessage(user, payload);
            }
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

        private void applySecretTheme(boolean on) {
            SwingUtilities.invokeLater(() -> {
                if (on) {
                    // 시크릿 모드도 배경은 일반 모드와 동일하게 유지
                    mainPanel.setBackground(Colors.BG_COLOR);
                    headerCard.setBackground(Colors.CARD_BG);
                    chatCard.setBackground(Colors.CARD_BG);
                    inputCard.setBackground(Colors.CARD_BG);

                    chatContainer.setBackground(Colors.BG_COLOR);
                    chatScroll.getViewport().setBackground(Colors.BG_COLOR);

                    // 텍스트 컬러도 기존 그대로 사용 (원하면 약간만 바꿔도 ok)
                    lblRoom.setForeground(Colors.TEXT_PRIMARY);
                    lblUser.setForeground(Colors.TEXT_PRIMARY);
                    lblStatusText.setForeground(Colors.TEXT_SECONDARY);
                    lblTypingIndicator.setForeground(Colors.TEXT_SECONDARY);

                    // 상태 아이콘만 포인트 컬러로 변경해서 "시크릿 ON" 티 내기
                    lblStatusIcon.setIcon(UiUtils.makeStatusIcon(Colors.SECRET_ACCENT));

                    // 입력창: 점선 테두리 + 기존 배경/텍스트
                    tfInput.setBackground(Colors.INPUT_BG);
                    tfInput.setForeground(Colors.TEXT_PRIMARY);
                    tfInput.setBorder(new DashedRoundedBorder(10, Colors.SECRET_ACCENT));

                } else {
                    // 🔄 일반 모드 복원
                    mainPanel.setBackground(Colors.BG_COLOR);
                    headerCard.setBackground(Colors.CARD_BG);
                    chatCard.setBackground(Colors.CARD_BG);
                    inputCard.setBackground(Colors.CARD_BG);

                    chatContainer.setBackground(Colors.BG_COLOR);
                    chatScroll.getViewport().setBackground(Colors.BG_COLOR);

                    lblRoom.setForeground(Colors.TEXT_PRIMARY);
                    lblUser.setForeground(Colors.TEXT_PRIMARY);
                    lblStatusText.setForeground(Colors.TEXT_SECONDARY);
                    lblTypingIndicator.setForeground(Colors.TEXT_SECONDARY);

                    lblStatusIcon.setIcon(UiUtils.makeStatusIcon(Colors.PRIMARY));

                    tfInput.setBackground(Colors.INPUT_BG);
                    tfInput.setForeground(Colors.TEXT_PRIMARY);
                    tfInput.setBorder(new RoundedBorder(10, Colors.INPUT_BORDER, Colors.PRIMARY));
                }

                // 전체 리프레시
                mainPanel.revalidate();
                mainPanel.repaint();
            });
        }

        private void commitChat(Runnable op) {
            Runnable apply = () -> {
                op.run();
                chatContainer.revalidate();
                chatContainer.repaint();
                SwingUtilities.invokeLater(() -> {
                    JScrollBar v = chatScroll.getVerticalScrollBar();
                    v.setValue(v.getMaximum()); // 맨 아래로
                });
            };
            if (SwingUtilities.isEventDispatchThread()) apply.run();
            else SwingUtilities.invokeLater(apply);
        }

        private void sendAsync(String msg) {
            if (client == null || msg == null || msg.isEmpty()) return;
            new Thread(() -> client.sendMessage(msg), "ui-outbound").start();
        }

        // 폭탄 아이콘
        private ImageIcon getBombIcon() {
            if (bombIcon != null) return bombIcon;
            try {
                java.net.URL url = getClass().getResource("/images/bomb.png");
                if (url != null) {
                    Image img = new ImageIcon(url).getImage()
                            .getScaledInstance(BOMB_ICON_SIZE, BOMB_ICON_SIZE, Image.SCALE_SMOOTH);
                    bombIcon = new ImageIcon(img);
                }
            } catch (Exception ignored) {}
            return bombIcon;
        }

        private void addMyEmojiMessage(String code) {
            String path = EmojiRegistry.findEmoji(code);
            if (path == null) { addMyMessage(code, false); return; }

            ImageIcon icon = loadEmojiIcon(path, EMOJI_CHAT_SIZE);
            if (icon == null) { addMyMessage(code, false); return; }

            commitChat(() -> {
                JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
                messagePanel.setOpaque(false);

                JLabel timeLabel = new JLabel(getCurrentTime());
                timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
                timeLabel.setForeground(Colors.TEXT_SECONDARY);

                // 말풍선 없이 이미지 라벨만
                JLabel emojiLabel = new JLabel(icon);
                // 큰 이미지라 여백도 살짝
                emojiLabel.setBorder(new EmptyBorder(6, 6, 6, 6));

                // 시간-이모지 순서는 말풍선 메시지와 동일 정렬
                messagePanel.add(timeLabel);
                messagePanel.add(emojiLabel);

                chatContainer.add(messagePanel);
                chatContainer.add(Box.createVerticalStrut(10));
            });
        }

        private void leaveRoomAndBackToList() {
            // 이 창을 닫을 때는 "연결 끊지 않는다" 플래그
            shouldDisconnect = false;

            // 1) 서버에 방 나가기 명령
            if (client != null) {
                sendAsync(Constants.CMD_LEAVE_ROOM);
            }

            // 2) 방 목록 창 다시 보여주기 + 활성화
            if (parentFrame != null) {
                parentFrame.setVisible(true);
                parentFrame.setEnabled(true);        // 혹시 입장할 때 disable 해놨다면 다시 활성화
                parentFrame.toFront();
                parentFrame.requestFocus();
            }

            // 3) 현재 채팅 창 닫기
            dispose();
        }

        private void openEmojiPicker() {
            JDialog dlg = new JDialog(this, "이모티콘", false);
            EmojiPickerPanel panel = new EmojiPickerPanel(code -> {
                boolean secretOn = secretMgr != null && secretMgr.isSecretOn();

                // 1) 로컬 즉시 렌더
                if (secretOn) {
                    secretMgr.addMySecretEmoji(code);
                }

                // 2) 비동기 전송(일관성 위해 sendAsync 권장)
                sendAsync(Constants.PKG_EMOJI + " " + code);

                dlg.dispose();
                tfInput.requestFocusInWindow();
            }, EMOJI_PICKER_SIZE); // 썸네일 좀 더 키움(권장 44~52)
            dlg.setContentPane(panel);
            dlg.pack();
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
        }


        private void addOtherEmojiMessage(String user, String code) {
            String path = EmojiRegistry.findEmoji(code);
            if (path == null) { addOtherMessage(user, code); return; }

            ImageIcon icon = loadEmojiIcon(path, EMOJI_CHAT_SIZE);
            if (icon == null) { addOtherMessage(user, code); return; }

            commitChat(() -> {
                JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
                messagePanel.setOpaque(false);

                JLabel avatar = new JLabel(getAvatarIcon(user));
                avatar.setPreferredSize(new Dimension(40, 40));

                JPanel right = new JPanel();
                right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
                right.setOpaque(false);

                JLabel nameLabel = new JLabel(user);
                nameLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
                nameLabel.setForeground(Colors.TEXT_SECONDARY);

                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                row.setOpaque(false);

                // 말풍선 제거: 이미지 라벨만
                JLabel emojiLabel = new JLabel(icon);
                emojiLabel.setBorder(new EmptyBorder(6, 6, 6, 6));

                JLabel timeLabel = new JLabel(getCurrentTime());
                timeLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
                timeLabel.setForeground(Colors.TEXT_SECONDARY);

                row.add(emojiLabel);
                row.add(timeLabel);

                right.add(nameLabel);
                right.add(Box.createVerticalStrut(4));
                right.add(row);

                messagePanel.add(avatar);
                messagePanel.add(right);

                chatContainer.add(messagePanel);
                chatContainer.add(Box.createVerticalStrut(10));
            });
        }


    }
