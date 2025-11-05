package chat.client.ui;

import chat.client.ChatClient;
import chat.util.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;

/**
 * OmokGameFrame - 게스트 "대기 중" 문제 해결 + 이모지 수정
 *
 * 🔧 수정사항:
 *   1. 게스트도 @game:start 메시지 제대로 받고 처리
 *   2. ⚪⚫ → ■□ (검정/흰색 네모)로 변경
 *   3. 플레이어 표시 동적 업데이트
 */
public class OmokGameFrame extends JFrame implements ChatClient.MessageListener {
    private static final Color BG_COLOR = new Color(240, 242, 245);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(31, 41, 55);
    private static final Color TEXT_SECONDARY = new Color(120, 130, 140);
    private static final Color PRIMARY = new Color(255, 159, 64);
    private static final Color SUCCESS = new Color(34, 197, 94);

    private OmokGamePanel gamePanel;
    private JLabel lblCurrentTurn;

    // 🔧 플레이어 섹션용 레이블
    private JLabel lblBlackPlayer;
    private JLabel lblWhitePlayer;

    private JButton btnRestart;
    private JButton btnQuit;

    private String myNickname;
    private String opponentNickname = "";
    private ChatClient client;
    private ChatFrame chatFrame;

    private int myColor = 1;
    private int opponentColor = 2;

    private boolean gameStarted = false;

    // 🔧 생성자 수정
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

        // 🔧 **이 부분을 생성자 마지막으로 옮김 (setVisible() 전)**
        System.out.println("[OMOK] 📋 게임 리스너 등록 시작");
        chatFrame.addGameListener(this);
        System.out.println("[OMOK] ✅ 게임 리스너 등록 완료");

        // 이제 게임 참여 요청 (리스너 등록 후)
        sendGameJoinMessage();
    }


    private void sendGameJoinMessage() {
        if (client != null) {
            client.sendMessage(Constants.CMD_GAME_JOIN + " omok");
        }
    }

    // ========== 헤더 ==========
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

    // ========== 콘텐츠 ==========
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

    // ========== 정보 패널 ==========
    private JPanel buildInfoPanel() {
        JPanel infoPanel = new RoundedPanel(15);
        infoPanel.setBackground(CARD_BG);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        infoPanel.setPreferredSize(new Dimension(280, 0));
        infoPanel.setMaximumSize(new Dimension(280, Integer.MAX_VALUE));

        // ========== 1. 현재 턴 섹션 ==========
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

        // ========== 2. 구분선 ==========
        JSeparator separator1 = new JSeparator();
        separator1.setMaximumSize(new Dimension(240, 1));
        separator1.setForeground(new Color(230, 230, 230));
        separator1.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(separator1);
        infoPanel.add(Box.createVerticalStrut(20));

        // ========== 3. 플레이어 섹션 ==========
        JLabel lblPlayersTitle = new JLabel("⚙️ 플레이어");
        lblPlayersTitle.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 14));
        lblPlayersTitle.setForeground(TEXT_PRIMARY);
        lblPlayersTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblPlayersTitle);
        infoPanel.add(Box.createVerticalStrut(12));

        // 🔧 흑돌 플레이어 (검정 네모)
        lblBlackPlayer = new JLabel("■ " + myNickname);
        lblBlackPlayer.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        lblBlackPlayer.setForeground(TEXT_PRIMARY);
        lblBlackPlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblBlackPlayer);
        infoPanel.add(Box.createVerticalStrut(8));

        // 🔧 백돌 플레이어 (흰색 네모)
        lblWhitePlayer = new JLabel("□ 대기 중...");
        lblWhitePlayer.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        lblWhitePlayer.setForeground(TEXT_SECONDARY);
        lblWhitePlayer.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(lblWhitePlayer);
        infoPanel.add(Box.createVerticalStrut(20));

        // ========== 4. 구분선 ==========
        JSeparator separator2 = new JSeparator();
        separator2.setMaximumSize(new Dimension(240, 1));
        separator2.setForeground(new Color(230, 230, 230));
        separator2.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(separator2);
        infoPanel.add(Box.createVerticalStrut(20));

        // ========== 5. 게임 규칙 ==========
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

    // ========== 푸터 ==========
    private JComponent buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        footer.setOpaque(false);
        footer.setPreferredSize(new Dimension(0, 50));

        btnRestart = createButton("새 게임", PRIMARY);
        btnRestart.setEnabled(false);
        btnRestart.addActionListener(e -> restartGame());

        btnQuit = createButton("게임 나가기", new Color(149, 165, 166));
        btnQuit.addActionListener(e -> quitGame());

        footer.add(btnRestart);
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

    // ========== 게임 로직 ==========
    public void restartGame() {
        gamePanel.restart();
        updateStatus();
    }

    public void quitGame() {
        if (client != null) {
            client.sendMessage(Constants.CMD_GAME_QUIT);
        }
        dispose();
    }

    // 🔧 플레이어 정보 업데이트 메서드
    private void updatePlayerInfo() {
        boolean iAmHost = myNickname.compareTo(opponentNickname) < 0;

        if (iAmHost) {
            lblBlackPlayer.setText("■ " + myNickname);
            lblBlackPlayer.setForeground(TEXT_PRIMARY);
            lblWhitePlayer.setText("□ " + opponentNickname);
            lblWhitePlayer.setForeground(TEXT_PRIMARY);
        } else {
            lblBlackPlayer.setText("■ " + opponentNickname);
            lblBlackPlayer.setForeground(TEXT_PRIMARY);
            lblWhitePlayer.setText("□ " + myNickname);
            lblWhitePlayer.setForeground(TEXT_PRIMARY);
        }
    }

    // 🔧 현재 턴 업데이트 메서드
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

            btnRestart.setEnabled(false);
        }
    }

    @Override
    public void onMessageReceived(String line) {
        System.out.println("[OMOK FRAME] 수신: " + line);

        // 🔧 순서 1️⃣: 게임 시작 (myColor 설정)
        if (line.startsWith(Constants.RESPONSE_GAME_START) || line.startsWith("@game:start")) {
            String opponentName = line.replace(Constants.RESPONSE_GAME_START, "")
                    .replace("@game:start", "")
                    .trim();

            SwingUtilities.invokeLater(() -> {
                gameStarted = true;
                opponentNickname = opponentName;

                // ✅ 여기서 myColor 설정!
                boolean iAmHost = myNickname.compareTo(opponentNickname) < 0;
                myColor = iAmHost ? 1 : 2;
                opponentColor = iAmHost ? 2 : 1;

                updatePlayerInfo();
                updateCurrentTurn();

                gamePanel.setOpponentNickname(opponentNickname);
                gamePanel.setGameEnabled(true);
                gamePanel.setMyTurn(iAmHost);
                gamePanel.repaint();

                System.out.println("[OMOK] ✅ 게임 시작 - myColor=" + myColor);
            });
            return;  // ✅ return 필수!
        }

        // 🔧 순서 2️⃣: 턴 정보 (@game:turn 은 myColor 설정 후)
        if (line.startsWith("@game:turn")) {
            SwingUtilities.invokeLater(() -> {
                try {
                    int turn = Integer.parseInt(line.substring("@game:turn".length()).trim());

                    // ✅ myColor가 이미 설정됨!
                    boolean myTurnNow = (turn == myColor);

                    System.out.println("[OMOK] 턴 정보: turn=" + turn +
                            ", myColor=" + myColor +
                            ", myTurnNow=" + myTurnNow);

                    gamePanel.setGameEnabled(myTurnNow);
                    gamePanel.setMyTurn(myTurnNow);
                    gamePanel.repaint();

                    updateCurrentTurn();
                } catch (Exception e) {
                    System.err.println("[ERROR] 턴 파싱 실패: " + line);
                }
            });
            return;  // ✅ return 필수!
        }

        // 🔧 순서 3️⃣: 돌 놓기
        if (line.startsWith("@game:move")) {  // ← 수정: RESPONSE_GAME_MOVE 대신 @game:move
            String[] parts = line.substring("@game:move".length()).trim().split(" ");
            if (parts.length >= 3) {
                try {
                    int row = Integer.parseInt(parts[0]);
                    int col = Integer.parseInt(parts[1]);
                    int player = Integer.parseInt(parts[2]);

                    SwingUtilities.invokeLater(() -> {
                        gamePanel.placeStone(row, col, player);
                        gamePanel.changeTurn();
                        gamePanel.repaint();
                        updateStatus();
                    });
                } catch (NumberFormatException e) {
                    System.err.println("돌 놓기 파싱 오류: " + line);
                }
            }
            return;  // ✅ return 필수!
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "서버 연결이 끊어졌습니다.", "연결 종료", JOptionPane.WARNING_MESSAGE);
            dispose();
        });
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