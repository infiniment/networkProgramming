package chat.ui.chat;

import chat.client.ChatClient;
import chat.util.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;

public class OmokGameFrame extends JFrame implements ChatClient.MessageListener {
    private static final Color BG_COLOR = new Color(240, 242, 245);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(31, 41, 55);
    private static final Color TEXT_SECONDARY = new Color(120, 130, 140);
    private static final Color PRIMARY = new Color(255, 159, 64);
    private static final Color SUCCESS = new Color(34, 197, 94);

    private OmokGamePanel gamePanel;
    private JLabel lblCurrentTurn;

    private JLabel lblBlackPlayer;
    private JLabel lblWhitePlayer;

    private JButton btnQuit;

    private String myNickname;
    private String opponentNickname = "";
    private ChatClient client;
    private ChatFrame chatFrame;

    private int myColor = 1;
    private int opponentColor = 2;
    private boolean gameStarted = false;

    public OmokGameFrame(String myNickname, ChatClient client, ChatFrame chatFrame) {
        this.myNickname = myNickname;
        this.client = client;
        this.chatFrame = chatFrame;
        this.opponentNickname = "";

        setTitle("오목 게임");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1150, 750);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        mainPanel.add(buildHeader(), BorderLayout.NORTH);
        mainPanel.add(buildContent(), BorderLayout.CENTER);
        mainPanel.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        gamePanel.setGameEnabled(false);

        chatFrame.addGameListener(this);

        // 이제 게임 참여 요청 (리스너 등록 후)
        sendGameJoinMessage();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (chatFrame != null) {
                    chatFrame.removeGameListener(OmokGameFrame.this);
                }
            }
        });
    }

    private void sendGameJoinMessage() {
        if (client != null) {
            client.sendMessage(Constants.CMD_GAME_JOIN + " omok");
        }
    }

    private JComponent buildHeader() {
        JPanel header = new RoundedPanel(15);
        header.setBackground(CARD_BG);
        header.setBorder(new EmptyBorder(12, 20, 12, 20));
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 50));

        JLabel title = new JLabel("⚫ 오목 게임");
        title.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);

        header.add(title, BorderLayout.WEST);
        return header;
    }

    private JComponent buildContent() {
        JPanel content = new JPanel(new BorderLayout(15, 0));
        content.setOpaque(false);

        JPanel gameAreaPanel = new RoundedPanel(15);
        gameAreaPanel.setBackground(CARD_BG);
        gameAreaPanel.setLayout(new GridBagLayout());
        gameAreaPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        gamePanel = new OmokGamePanel(this);
        gamePanel.setPreferredSize(new Dimension(580, 580));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gameAreaPanel.add(gamePanel, gbc);

        JPanel infoPanel = buildInfoPanel();

        content.add(gameAreaPanel, BorderLayout.CENTER);
        content.add(infoPanel, BorderLayout.EAST);

        return content;
    }

    private JPanel buildInfoPanel() {
        JPanel infoPanel = new RoundedPanel(15);
        infoPanel.setBackground(CARD_BG);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        infoPanel.setPreferredSize(new Dimension(280, 0));
        infoPanel.setMaximumSize(new Dimension(280, Integer.MAX_VALUE));

        JLabel lblTurnTitle = new JLabel("🎮 현재 턴");
        lblTurnTitle.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
        lblTurnTitle.setForeground(TEXT_PRIMARY);
        lblTurnTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblTurnTitle);
        infoPanel.add(Box.createVerticalStrut(12));

        lblCurrentTurn = new JLabel("게임 시작 대기 중...");
        lblCurrentTurn.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        lblCurrentTurn.setForeground(PRIMARY);
        lblCurrentTurn.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblCurrentTurn);
        infoPanel.add(Box.createVerticalStrut(20));

        JSeparator separator1 = new JSeparator();
        separator1.setMaximumSize(new Dimension(240, 1));
        separator1.setForeground(new Color(230, 230, 230));
        separator1.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(separator1);
        infoPanel.add(Box.createVerticalStrut(20));

        JLabel lblPlayersTitle = new JLabel("⚙️ 플레이어");
        lblPlayersTitle.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
        lblPlayersTitle.setForeground(TEXT_PRIMARY);
        lblPlayersTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblPlayersTitle);
        infoPanel.add(Box.createVerticalStrut(12));

        lblBlackPlayer = new JLabel("■ " + myNickname);
        lblBlackPlayer.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        lblBlackPlayer.setForeground(TEXT_PRIMARY);
        lblBlackPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblBlackPlayer);
        infoPanel.add(Box.createVerticalStrut(8));

        lblWhitePlayer = new JLabel("□ 대기 중...");
        lblWhitePlayer.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        lblWhitePlayer.setForeground(TEXT_SECONDARY);
        lblWhitePlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblWhitePlayer);
        infoPanel.add(Box.createVerticalStrut(20));

        JSeparator separator2 = new JSeparator();
        separator2.setMaximumSize(new Dimension(240, 1));
        separator2.setForeground(new Color(230, 230, 230));
        separator2.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(separator2);
        infoPanel.add(Box.createVerticalStrut(20));

        JLabel lblRulesTitle = new JLabel("📋 게임 규칙");
        lblRulesTitle.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
        lblRulesTitle.setForeground(TEXT_PRIMARY);
        lblRulesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblRulesTitle);
        infoPanel.add(Box.createVerticalStrut(10));

        String[] rules = {
                "• 15×15 바둑판",
                "• 검은돌(■)이 먼저 시작",
                "• 자신의 돌만 놓기 가능",
                "• 5개 연속 달성 시 승리"
        };

        for (String rule : rules) {
            JLabel ruleLabel = new JLabel(rule);
            ruleLabel.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
            ruleLabel.setForeground(TEXT_SECONDARY);
            ruleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(ruleLabel);
            infoPanel.add(Box.createVerticalStrut(4));
        }

        infoPanel.add(Box.createVerticalGlue());
        return infoPanel;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        footer.setOpaque(false);
        footer.setPreferredSize(new Dimension(0, 50));

        btnQuit = createButton("게임 나가기", new Color(149, 165, 166));
        btnQuit.addActionListener(e -> quitGame());

        footer.add(btnQuit);

        return footer;
    }

    private JButton createButton(String text, Color color) {
        JButton btn = new JButton(text) {
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
                g2.setColor(hover ? color.darker() : color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(110, 40));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);

        return btn;
    }

    public void quitGame() {
        if (chatFrame != null) {
            chatFrame.removeGameListener(this);
            System.out.println("[OMOK] 게임 리스너 제거됨");
        }

        if (client != null) {
            client.sendMessage(Constants.CMD_GAME_QUIT);
        }
        dispose();
    }

    private void updatePlayerInfo() {
        if (opponentNickname == null || opponentNickname.isEmpty()) {
            lblBlackPlayer.setText("■ " + myNickname);
            lblWhitePlayer.setText("□ -");
            lblBlackPlayer.setForeground(TEXT_PRIMARY);
            lblWhitePlayer.setForeground(TEXT_PRIMARY);
            return;
        }

        if (myColor == 1) {
            lblBlackPlayer.setText("■ " + myNickname);
            lblWhitePlayer.setText("□ " + opponentNickname);
        } else {
            lblBlackPlayer.setText("■ " + opponentNickname);
            lblWhitePlayer.setText("□ " + myNickname);
        }

        lblBlackPlayer.setForeground(TEXT_PRIMARY);
        lblWhitePlayer.setForeground(TEXT_PRIMARY);
    }


    // 현재 턴 업데이트 메서드
    private void updateCurrentTurn() {
        if (!gameStarted) {
            lblCurrentTurn.setText("게임 시작 대기 중...");
            lblCurrentTurn.setForeground(PRIMARY);
            return;
        }

        int currentPlayer = gamePanel.getCurrentPlayer();
        String playerName = (currentPlayer == myColor) ? myNickname : opponentNickname;
        String stoneSymbol = (currentPlayer == 1) ? "■" : "□";

        lblCurrentTurn.setText(stoneSymbol + " " + playerName);
        lblCurrentTurn.setForeground((currentPlayer == myColor) ? new Color(34, 197, 94) : PRIMARY);
    }

    public void updateStatus() {
        updateCurrentTurn();

        if (gamePanel.checkWin()) {
            int winnerColor = gamePanel.getCurrentPlayer();
            String winner = (winnerColor == myColor) ? myNickname : opponentNickname;
            lblCurrentTurn.setText("🎉 " + winner + "님 승리!");
            lblCurrentTurn.setForeground(new Color(34, 197, 94));

            if (client != null) {
                client.sendMessage(Constants.CMD_GAME_QUIT);
            }

        }
    }

    @Override
    public void onMessageReceived(String line) {
        System.out.println("[OMOK FRAME] 수신: " + line);

        // 게임 시작
        if (line.startsWith(Constants.RESPONSE_GAME_START) || line.startsWith("@game:start")) {
            String opponentName = line.replace(Constants.RESPONSE_GAME_START, "")
                    .replace("@game:start", "")
                    .trim();

            SwingUtilities.invokeLater(() -> {
                opponentNickname = opponentName;
                gameStarted = true;

                gamePanel.setOpponentNickname(opponentNickname);
                updatePlayerInfo();
                updateCurrentTurn();
                gamePanel.repaint();
            });
            return;
        }

        // 내 색/첫 턴 정보
        if (line.startsWith(Constants.RESPONSE_GAME_TURN)) {
            String value = line.substring(Constants.RESPONSE_GAME_TURN.length()).trim();
            try {
                int turn = Integer.parseInt(value);

                SwingUtilities.invokeLater(() -> {
                    boolean iAmHost = (turn == 1);
                    myColor = iAmHost ? 1 : 2;
                    opponentColor = iAmHost ? 2 : 1;

                    gameStarted = true;

                    // 항상 흑돌이 먼저 시작
                    gamePanel.setCurrentPlayer(1);

                    // 흑돌이면 첫 턴, 백돌이면 기다리기
                    boolean myTurnNow = (myColor == 1);
                    gamePanel.setGameEnabled(myTurnNow);
                    gamePanel.setMyTurn(myTurnNow);

                    updatePlayerInfo();
                    updateCurrentTurn();
                    gamePanel.repaint();

                    System.out.println("[OMOK] 게임 시작 - myColor=" + myColor + ", myTurn=" + myTurnNow);
                });
            } catch (NumberFormatException e) {
                }
            return;
        }

        // 돌 놓기
        if (line.startsWith("@game:move")) {
            String[] parts = line.substring("@game:move".length()).trim().split(" ");
            if (parts.length >= 3) {
                try {
                    int row = Integer.parseInt(parts[0]);
                    int col = Integer.parseInt(parts[1]);
                    int player = Integer.parseInt(parts[2]);

                    SwingUtilities.invokeLater(() -> {
                        System.out.println("[OMOK] 돌 배치: (" + row + "," + col + ") 색상=" + player);

                        // 돌 놓기
                        gamePanel.placeStone(row, col, player);

                        // 승리 체크
                        if (gamePanel.checkWinAt(row, col, player)) {
                            gamePanel.setGameOver(true);
                            gamePanel.setWinnerColor(player);
                            updateStatus();
                            gamePanel.repaint();
                            return;
                        }

                        // 턴 변경
                        gamePanel.changeTurn();

                        // 내 턴이면 활성화
                        int nextTurn = gamePanel.getCurrentPlayer();
                        boolean myTurnNow = (nextTurn == myColor);
                        gamePanel.setGameEnabled(myTurnNow);
                        gamePanel.setMyTurn(myTurnNow);

                        updateStatus();
                        gamePanel.repaint();
                    });
                } catch (NumberFormatException e) {
                    System.err.println("돌 놓기 파싱 오류: " + line);
                }
            }
            return;
        }
}

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(this::dispose);
    }

    public int getMyColor() { return myColor; }
    public int getOpponentColor() { return opponentColor; }
    public String getMyNickname() { return myNickname; }
    public String getOpponentNickname() { return opponentNickname; }
    public ChatClient getClient() { return client; }

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
        }
        return new Font("Dialog", style, size);
    }

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