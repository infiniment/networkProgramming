package chat.ui.chat;

import chat.client.ChatClient;
import chat.ui.common.Colors;
import chat.ui.fonts.FontManager;
import chat.util.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Br31GameFrame - 베스킨라빈스31 게임 화면 (개선 버전)
 * 🎲 3-5명 멀티플레이어 게임
 */
public class Br31GameFrame extends JFrame implements ChatClient.MessageListener {

    private static final Color BG_COLOR = new Color(240, 242, 245);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(31, 41, 55);
    private static final Color TEXT_SECONDARY = new Color(120, 130, 140);
    private static final Color PRIMARY = new Color(255, 159, 64);
    private static final Color SUCCESS = new Color(34, 197, 94);
    private static final Color DANGER = new Color(239, 68, 68);
    private static final Color NUMBER_BG = new Color(250, 250, 250);
    private static final Color NUMBER_DANGER = new Color(255, 230, 230);

    private String myNickname;
    private ChatClient client;
    private ChatFrame chatFrame;

    private boolean isHost = false;
    private int maxPlayers = 0;
    private List<String> players = new ArrayList<>();
    private String currentTurnPlayer = "";
    private int currentCount = 0;

    private GameState gameState = GameState.SETUP;

    // UI 컴포넌트
    private JPanel mainPanel;
    private JPanel setupPanel;
    private JPanel waitingPanel;
    private JPanel gamePanel;
    private JPanel resultPanel;

    // 게임 화면 컴포넌트
    private JLabel lblCurrentCount;
    private JLabel lblCurrentTurn;
    private JPanel numberGridPanel;
    private JPanel playerListPanelInGame;
    private JPanel numberButtonPanel;

    private enum GameState {
        SETUP, WAITING, PLAYING, FINISHED
    }

    public Br31GameFrame(String myNickname, ChatClient client, ChatFrame chatFrame) {
        this.myNickname = myNickname;
        this.client = client;
        this.chatFrame = chatFrame;

        setTitle("🍦 베스킨라빈스31");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setResizable(false);

        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(BG_COLOR);

        buildSetupPanel();
        buildWaitingPanel();
        buildGamePanel();
        buildResultPanel();

        mainPanel.add(setupPanel, "SETUP");
        mainPanel.add(waitingPanel, "WAITING");
        mainPanel.add(gamePanel, "GAME");
        mainPanel.add(resultPanel, "RESULT");

        setContentPane(mainPanel);

        chatFrame.addGameListener(this);
        client.sendMessage(Constants.CMD_GAME_JOIN + " br31");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (chatFrame != null) {
                    chatFrame.removeGameListener(Br31GameFrame.this);
                }
            }
        });
    }

    // ========== 1. 셋업 화면 ==========
    private void buildSetupPanel() {
        setupPanel = new JPanel(new BorderLayout(20, 20));
        setupPanel.setBackground(BG_COLOR);
        setupPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("🍦 베스킨라빈스31", SwingConstants.CENTER);
        title.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 32));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("게임에 참여할 인원을 선택하세요", SwingConstants.CENTER);
        subtitle.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_SECONDARY);

        JLabel subtext = new JLabel("선택한 인원이 모두 모이면 게임이 시작됩니다", SwingConstants.CENTER);
        subtext.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        subtext.setForeground(TEXT_SECONDARY);

        JPanel headerPanel = new JPanel(new GridLayout(3, 1, 0, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(title);
        headerPanel.add(subtitle);
        headerPanel.add(subtext);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(new EmptyBorder(40, 80, 40, 80));

        for (int i = 3; i <= 5; i++) {
            final int count = i;
            JButton btn = createPlayerCountButton(count + "명");
            btn.addActionListener(e -> selectPlayerCount(count));
            buttonPanel.add(btn);
        }

        setupPanel.add(headerPanel, BorderLayout.NORTH);
        setupPanel.add(buttonPanel, BorderLayout.CENTER);
    }

    // ========== 2. 대기 화면 ==========
    private void buildWaitingPanel() {
        waitingPanel = new JPanel(new BorderLayout(20, 20));
        waitingPanel.setBackground(BG_COLOR);
        waitingPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("🍦 대기 중...", SwingConstants.CENTER);
        title.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 28));
        title.setForeground(TEXT_PRIMARY);

        JLabel lblStatus = new JLabel("플레이어를 기다리는 중...", SwingConstants.CENTER);
        lblStatus.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 16));
        lblStatus.setForeground(TEXT_SECONDARY);

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        headerPanel.setOpaque(false);
        headerPanel.add(title);
        headerPanel.add(lblStatus);

        JPanel playerListPanel = new JPanel();
        playerListPanel.setLayout(new BoxLayout(playerListPanel, BoxLayout.Y_AXIS));
        playerListPanel.setOpaque(false);
        playerListPanel.setBorder(new EmptyBorder(20, 100, 20, 100));

        JButton btnCancel = createActionButton("나가기", new Color(149, 165, 166));
        btnCancel.addActionListener(e -> {
            client.sendMessage(Constants.CMD_GAME_QUIT);
            dispose();
        });

        waitingPanel.add(headerPanel, BorderLayout.NORTH);
        waitingPanel.add(playerListPanel, BorderLayout.CENTER);
        waitingPanel.add(btnCancel, BorderLayout.SOUTH);
    }

    // ========== 3. 게임 화면 (개선 버전) ==========
    private void buildGamePanel() {
        gamePanel = new JPanel(new BorderLayout(15, 15));
        gamePanel.setBackground(BG_COLOR);
        gamePanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // ========== 왼쪽: 숫자판 + 현재 턴 ==========
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setOpaque(false);

        // 현재 턴 표시
        lblCurrentTurn = new JLabel("게임 시작!", SwingConstants.CENTER);
        lblCurrentTurn.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 18));
        lblCurrentTurn.setForeground(TEXT_PRIMARY);
        lblCurrentTurn.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 숫자판 (1~31)
        JPanel numberGridContainer = new RoundedPanel(15);
        numberGridContainer.setBackground(CARD_BG);
        numberGridContainer.setBorder(new EmptyBorder(20, 20, 20, 20));
        numberGridContainer.setLayout(new BorderLayout());

        JLabel gridTitle = new JLabel("🔢 숫자판", SwingConstants.CENTER);
        gridTitle.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        gridTitle.setForeground(TEXT_PRIMARY);
        gridTitle.setBorder(new EmptyBorder(0, 0, 15, 0));

        numberGridPanel = new JPanel(new GridLayout(4, 8, 8, 8));
        numberGridPanel.setOpaque(false);
        initializeNumberGrid();

        numberGridContainer.add(gridTitle, BorderLayout.NORTH);
        numberGridContainer.add(numberGridPanel, BorderLayout.CENTER);

        leftPanel.add(lblCurrentTurn, BorderLayout.NORTH);
        leftPanel.add(numberGridContainer, BorderLayout.CENTER);

        // ========== 오른쪽: 플레이어 목록 + 게임 규칙 ==========
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(280, 0));

        // 플레이어 목록
        JPanel playerSection = new RoundedPanel(15);
        playerSection.setBackground(CARD_BG);
        playerSection.setBorder(new EmptyBorder(20, 20, 20, 20));
        playerSection.setLayout(new BorderLayout(10, 10));

        JLabel playerTitle = new JLabel("👥 플레이어");
        playerTitle.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        playerTitle.setForeground(TEXT_PRIMARY);

        playerListPanelInGame = new JPanel();
        playerListPanelInGame.setLayout(new BoxLayout(playerListPanelInGame, BoxLayout.Y_AXIS));
        playerListPanelInGame.setOpaque(false);

        playerSection.add(playerTitle, BorderLayout.NORTH);
        playerSection.add(playerListPanelInGame, BorderLayout.CENTER);

        // 게임 규칙
        JPanel rulesSection = new RoundedPanel(15);
        rulesSection.setBackground(CARD_BG);
        rulesSection.setBorder(new EmptyBorder(20, 20, 20, 20));
        rulesSection.setLayout(new BorderLayout(10, 10));

        JLabel rulesTitle = new JLabel("📋 게임 규칙");
        rulesTitle.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
        rulesTitle.setForeground(TEXT_PRIMARY);

        String[] rules = {
                "• 순서대로 1~3개의 숫자 선택",
                "• 31을 외치는 사람이 패배",
                "• 연속된 숫자만 선택 가능"
        };

        JPanel rulesContent = new JPanel();
        rulesContent.setLayout(new BoxLayout(rulesContent, BoxLayout.Y_AXIS));
        rulesContent.setOpaque(false);

        for (String rule : rules) {
            JLabel ruleLabel = new JLabel(rule);
            ruleLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
            ruleLabel.setForeground(TEXT_SECONDARY);
            ruleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rulesContent.add(ruleLabel);
            rulesContent.add(Box.createVerticalStrut(8));
        }

        rulesSection.add(rulesTitle, BorderLayout.NORTH);
        rulesSection.add(rulesContent, BorderLayout.CENTER);

        rightPanel.add(playerSection, BorderLayout.CENTER);
        rightPanel.add(rulesSection, BorderLayout.SOUTH);

        // ========== 하단: 숫자 선택 버튼 ==========
        JPanel bottomPanel = new RoundedPanel(15);
        bottomPanel.setBackground(CARD_BG);
        bottomPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        bottomPanel.setLayout(new BorderLayout(10, 10));

        JLabel btnTitle = new JLabel("🎯 선택하기", SwingConstants.CENTER);
        btnTitle.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
        btnTitle.setForeground(TEXT_PRIMARY);

        numberButtonPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        numberButtonPanel.setOpaque(false);

        for (int i = 1; i <= 3; i++) {
            final int count = i;
            JButton btn = createNumberButton(count + "개");
            btn.addActionListener(e -> selectNumbers(count));
            numberButtonPanel.add(btn);
        }

        bottomPanel.add(btnTitle, BorderLayout.NORTH);
        bottomPanel.add(numberButtonPanel, BorderLayout.CENTER);

        gamePanel.add(leftPanel, BorderLayout.CENTER);
        gamePanel.add(rightPanel, BorderLayout.EAST);
        gamePanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    // ========== 숫자판 초기화 ==========
    private void initializeNumberGrid() {
        numberGridPanel.removeAll();
        for (int i = 1; i <= 31; i++) {
            JLabel numLabel = createNumberLabel(i);
            numberGridPanel.add(numLabel);
        }
        // 빈 칸 채우기 (4x8 = 32칸, 31개 숫자)
        numberGridPanel.add(new JLabel());
    }

    private JLabel createNumberLabel(int num) {
        JLabel label = new JLabel(String.valueOf(num), SwingConstants.CENTER);
        label.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        label.setForeground(TEXT_SECONDARY);
        label.setOpaque(true);
        label.setBackground(NUMBER_BG);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // 라운드 처리
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                new EmptyBorder(8, 8, 8, 8)
        ));

        return label;
    }

    // ========== 숫자판 업데이트 ==========
    private void updateNumberGrid(int count) {
        SwingUtilities.invokeLater(() -> {
            Component[] components = numberGridPanel.getComponents();
            for (int i = 0; i < components.length && i < count; i++) {
                if (components[i] instanceof JLabel) {
                    JLabel label = (JLabel) components[i];

                    // 31이면 빨간색, 25~30이면 주황색
                    if (i + 1 == 31) {
                        label.setBackground(DANGER);
                        label.setForeground(Color.WHITE);
                    } else if (i + 1 >= 25) {
                        label.setBackground(new Color(255, 200, 150));
                        label.setForeground(TEXT_PRIMARY);
                    } else {
                        label.setBackground(PRIMARY);
                        label.setForeground(Color.WHITE);
                    }

                    label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(PRIMARY.darker(), 2),
                            new EmptyBorder(8, 8, 8, 8)
                    ));
                }
            }
            numberGridPanel.revalidate();
            numberGridPanel.repaint();
        });
    }

    // ========== 플레이어 목록 업데이트 (게임 중) ==========
    private void updatePlayerListInGame() {
        SwingUtilities.invokeLater(() -> {
            playerListPanelInGame.removeAll();

            for (String player : players) {
                JPanel playerItem = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
                playerItem.setOpaque(false);
                playerItem.setMaximumSize(new Dimension(240, 40));

                // 현재 턴 플레이어 표시
                boolean isCurrentTurn = player.equals(currentTurnPlayer);

                JLabel indicator = new JLabel(isCurrentTurn ? "▶" : "  ");
                indicator.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
                indicator.setForeground(PRIMARY);

                JLabel nameLabel = new JLabel(player);
                nameLabel.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 13));
                nameLabel.setForeground(isCurrentTurn ? PRIMARY : TEXT_PRIMARY);

                playerItem.add(indicator);
                playerItem.add(nameLabel);

                playerListPanelInGame.add(playerItem);
                playerListPanelInGame.add(Box.createVerticalStrut(5));
            }

            playerListPanelInGame.revalidate();
            playerListPanelInGame.repaint();
        });
    }

    // ========== 버튼 생성 메서드들 ==========
    private JButton createPlayerCountButton(String text) {
        JButton btn = new JButton(text) {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? PRIMARY.darker() : PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 24));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(150, 150));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createNumberButton(String text) {
        JButton btn = new JButton(text) {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        if (isEnabled()) { hover = true; repaint(); }
                    }
                    public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = isEnabled() ? (hover ? PRIMARY.darker() : PRIMARY) : Color.LIGHT_GRAY;
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 18));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(150, 60));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setEnabled(false);
        return btn;
    }

    private JButton createActionButton(String text, Color color) {
        JButton btn = new JButton(text) {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    public void mouseExited(MouseEvent e) { hover = false; repaint(); }
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
        btn.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(120, 45));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ========== 게임 로직 메서드들 (기존 유지) ==========
    private void selectPlayerCount(int count) {
        isHost = true;
        maxPlayers = count;
        players.add(myNickname);
        client.sendMessage(Constants.CMD_GAME_JOIN + " br31 " + count);
        gameState = GameState.WAITING;
        showWaitingPanel();
    }

    private void showWaitingPanel() {
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "WAITING");
    }

    private void selectNumbers(int count) {
        if (!isMyTurn()) return;

        int[] numbers = new int[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = currentCount + i + 1;
        }

        if (numbers[numbers.length - 1] > 31) {
            JOptionPane.showMessageDialog(this, "31을 초과할 수 없습니다!", "오류", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String numbersStr = Arrays.toString(numbers).replaceAll("[\\[\\] ]", "");
        client.sendMessage(Constants.CMD_GAME_MOVE + " " + numbersStr);
        setNumberButtonsEnabled(false);
    }

    private void setNumberButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            for (Component comp : numberButtonPanel.getComponents()) {
                if (comp instanceof JButton) {
                    comp.setEnabled(enabled);
                }
            }
        });
    }

    private boolean isMyTurn() {
        return currentTurnPlayer.equals(myNickname);
    }

    // ========== 4. 결과 화면 (개선 버전) ==========
    private void buildResultPanel() {
        resultPanel = new JPanel(new BorderLayout(20, 20));
        resultPanel.setBackground(BG_COLOR);
        resultPanel.setBorder(new EmptyBorder(60, 60, 60, 60));

        // ========== 중앙: 결과 텍스트 ==========
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("", SwingConstants.CENTER);
        lblTitle.setFont(FontManager.get("BMDOHYEON_ttf.ttf", Font.BOLD, 72));
        lblTitle.setForeground(TEXT_PRIMARY);

        JLabel lblSubtext = new JLabel("", SwingConstants.CENTER);
        lblSubtext.setFont(FontManager.get("BMHANNAAir_ttf.ttf", Font.PLAIN, 20));
        lblSubtext.setForeground(TEXT_SECONDARY);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 20, 0);
        centerPanel.add(lblTitle, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(20, 0, 0, 0);
        centerPanel.add(lblSubtext, gbc);

        // ========== 하단: 버튼들 ==========
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setPreferredSize(new Dimension(0, 60));

        JButton btnClose = createActionButton("게임 나가기", new Color(149, 165, 166));
        btnClose.addActionListener(e -> {
            client.sendMessage(Constants.CMD_GAME_QUIT);
            dispose();
        });

        bottomPanel.add(btnClose);

        resultPanel.add(centerPanel, BorderLayout.CENTER);
        resultPanel.add(bottomPanel, BorderLayout.SOUTH);

        // ✅ 라벨들을 저장해서 나중에 접근
        resultPanel.putClientProperty("lblTitle", lblTitle);
        resultPanel.putClientProperty("lblSubtext", lblSubtext);
    }

    private void showResult(String loser) {
        SwingUtilities.invokeLater(() -> {
            // resultPanel에 저장된 라벨들 가져오기
            JLabel lblTitle = (JLabel) resultPanel.getClientProperty("lblTitle");
            JLabel lblSubtext = (JLabel) resultPanel.getClientProperty("lblSubtext");

            boolean iWon = !loser.equals(myNickname);

            if (iWon) {
                lblTitle.setText("🎉 승리! 🎉");
                lblTitle.setForeground(SUCCESS);
                lblSubtext.setText("축하합니다! 게임에서 이기셨습니다.");
                lblSubtext.setForeground(SUCCESS);
            } else {
                lblTitle.setText("💀 패배 💀");
                lblTitle.setForeground(DANGER);
                lblSubtext.setText(loser + "님이 31을 외쳤습니다.");
                lblSubtext.setForeground(DANGER);
            }

            CardLayout cl = (CardLayout) mainPanel.getLayout();
            cl.show(mainPanel, "RESULT");
        });
    }

    // ========== 메시지 수신 처리 (기존 로직 유지 + 업데이트 추가) ==========
    @Override
    public void onMessageReceived(String line) {
        System.out.println("[BR31-FRAME] 수신: " + line);

        if (line.startsWith(Constants.RESPONSE_GAME_WAITING + " br31")) {
            String[] parts = line.split(" ");

            SwingUtilities.invokeLater(() -> {
                if (parts.length > 2 && parts[2].equals("host")) {
                    // 호스트만 SETUP 화면
                    isHost = true;
                    gameState = GameState.SETUP;
                    CardLayout cl = (CardLayout) mainPanel.getLayout();
                    cl.show(mainPanel, "SETUP");
                } else {
                    // 게스트는 WAITING 화면
                    gameState = GameState.WAITING;
                    CardLayout cl = (CardLayout) mainPanel.getLayout();
                    cl.show(mainPanel, "WAITING");
                }
            });
            return;
        }

        if (line.startsWith(Constants.RESPONSE_GAME_START + " br31 ")) {
            String playerList = line.substring((Constants.RESPONSE_GAME_START + " br31 ").length()).trim();
            players = Arrays.asList(playerList.split(","));

            SwingUtilities.invokeLater(() -> {
                gameState = GameState.PLAYING;
                updatePlayerListInGame();
                CardLayout cl = (CardLayout) mainPanel.getLayout();
                cl.show(mainPanel, "GAME");
            });
            return;
        }

        if (line.startsWith("@game:turn ")) {
            currentTurnPlayer = line.substring("@game:turn ".length()).trim();

            SwingUtilities.invokeLater(() -> {
                updatePlayerListInGame();
                lblCurrentTurn.setText(currentTurnPlayer + "님의 차례");

                if (isMyTurn()) {
                    lblCurrentTurn.setForeground(SUCCESS);
                    setNumberButtonsEnabled(true);
                } else {
                    lblCurrentTurn.setForeground(TEXT_PRIMARY);
                    setNumberButtonsEnabled(false);
                }
            });
            return;
        }

        if (line.startsWith("@game:update ")) {
            String[] parts = line.substring("@game:update ".length()).split(" ", 3);
            if (parts.length >= 2) {
                int newCount = Integer.parseInt(parts[0]);

                SwingUtilities.invokeLater(() -> {
                    currentCount = newCount;
                    updateNumberGrid(newCount);
                });
            }
            return;
        }

        if (line.startsWith(Constants.RESPONSE_GAME_END + " br31 loser=")) {
            String loser = line.substring((Constants.RESPONSE_GAME_END + " br31 loser=").length()).trim();
            SwingUtilities.invokeLater(() -> {
                gameState = GameState.FINISHED;
                showResult(loser);
            });
            return;
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "서버 연결이 끊어졌습니다.", "연결 종료", JOptionPane.WARNING_MESSAGE);
            dispose();
        });
    }

    // ========== RoundedPanel 내부 클래스 ==========
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
}