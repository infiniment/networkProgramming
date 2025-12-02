package chat.ui.chat;

import chat.util.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class OmokGamePanel extends JPanel {
    private static final int BOARD_SIZE = 15;
    private static final int CELL_SIZE = 35;
    private static final Color PRIMARY = new Color(255, 159, 64);

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int currentPlayer = 1;

    private OmokGameFrame gameFrame;
    private boolean gameOver = false;
    private int winnerColor = 0;
    private String opponentNickname = "";
    private boolean gameEnabled = false;
    private boolean myTurn = false;
    private int hoverRow = -1;
    private int hoverCol = -1;

    public OmokGamePanel(OmokGameFrame gameFrame) {
        this.gameFrame = gameFrame;

        setBackground(new Color(222, 184, 135));
        setOpaque(true);
        setDoubleBuffered(true);

        setPreferredSize(new Dimension(580, 580));
        setMinimumSize(new Dimension(580, 580));
        setMaximumSize(new Dimension(580, 580));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!gameEnabled || !myTurn) {

                    if (!gameOver && isBoardEmpty()) {
                        int myColor = gameFrame.getMyColor();
                        if (myColor == 2) { // 백돌
                            JOptionPane.showMessageDialog(
                                    OmokGamePanel.this,
                                    "흑돌이 먼저 두어야 합니다!",
                                    "알림",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    }
                    System.out.println("[OMOK-PANEL] 클릭 무시: gameEnabled=" + gameEnabled +
                            ", myTurn=" + myTurn);
                    return;
                }
                handleClick(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCol = -1;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!gameEnabled || !myTurn || gameOver) {
                    if (hoverRow != -1 || hoverCol != -1) {
                        hoverRow = -1;
                        hoverCol = -1;
                        repaint();
                    }
                    return;
                }

                int panelWidth = getWidth();
                int panelHeight = getHeight();
                int boardPixelSize = CELL_SIZE * BOARD_SIZE;
                int boardX = (panelWidth - boardPixelSize) / 2;
                int boardY = (panelHeight - boardPixelSize) / 2;

                int col = Math.round((e.getX() - boardX - CELL_SIZE / 2f) / CELL_SIZE);
                int row = Math.round((e.getY() - boardY - CELL_SIZE / 2f) / CELL_SIZE);

                if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE && board[row][col] == 0) {
                    if (hoverRow != row || hoverCol != col) {
                        hoverRow = row;
                        hoverCol = col;
                        repaint();
                    }
                } else {
                    if (hoverRow != -1 || hoverCol != -1) {
                        hoverRow = -1;
                        hoverCol = -1;
                        repaint();
                    }
                }
            }
        });

        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        initBoard();
    }
    private boolean isBoardEmpty() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private void initBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = 0;
            }
        }
        currentPlayer = 1;
        gameOver = false;
        winnerColor = 0;
    }

    public void setGameEnabled(boolean enabled) {
        this.gameEnabled = enabled;
        updateCursor();
        repaint();
    }

    public void setOpponentNickname(String opponentNickname) {
        this.opponentNickname = opponentNickname;
        repaint();
    }

    public void setMyTurn(boolean myTurn) {
        this.myTurn = myTurn;
        updateCursor();
    }

    public boolean isMyTurn() {
        return myTurn;
    }
    private void updateCursor() {
        if (gameEnabled && myTurn && !gameOver) {
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBoard(g2);
        drawStones(g2);

        if (hoverRow >= 0 && hoverCol >= 0 && gameEnabled && myTurn && !gameOver) {
            drawPreview(g2);
        }

        if (!gameEnabled || (opponentNickname == null || opponentNickname.isEmpty())) {
            drawWaitingOverlay(g2);
        }

        if (gameOver) {
            drawGameOverMessage(g2);
        }

        g2.dispose();
    }

    private void drawPreview(Graphics2D g2) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int boardPixelSize = CELL_SIZE * BOARD_SIZE;
        int x = (panelWidth - boardPixelSize) / 2;
        int y = (panelHeight - boardPixelSize) / 2;

        int cellX = x + hoverCol * CELL_SIZE;
        int cellY = y + hoverRow * CELL_SIZE;
        int stoneRadius = (int)(CELL_SIZE * 0.4);

        g2.setColor(new Color(255, 200, 100, 80));
        g2.setStroke(new BasicStroke(3));

        // 가로선 하이라이트
        g2.drawLine(x, cellY, x + boardPixelSize, cellY);
        // 세로선 하이라이트
        g2.drawLine(cellX, y, cellX, y + boardPixelSize);

        g2.setColor(new Color(255, 159, 64, 120));
        g2.fillOval(cellX - 6, cellY - 6, 12, 12);

        int myColor = gameFrame.getMyColor();

        if (myColor == 1) {
            // 검은돌 미리보기
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillOval(cellX - stoneRadius, cellY - stoneRadius,
                    stoneRadius * 2, stoneRadius * 2);
            g2.setColor(new Color(50, 50, 50, 150));
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(cellX - stoneRadius, cellY - stoneRadius,
                    stoneRadius * 2, stoneRadius * 2);
        } else {
            // 흰돌 미리보기
            g2.setColor(new Color(255, 255, 255, 150));
            g2.fillOval(cellX - stoneRadius, cellY - stoneRadius,
                    stoneRadius * 2, stoneRadius * 2);
            g2.setColor(new Color(0, 0, 0, 150));
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(cellX - stoneRadius, cellY - stoneRadius,
                    stoneRadius * 2, stoneRadius * 2);
        }
    }

    private void drawWaitingOverlay(Graphics2D g2) {
        if (opponentNickname != null && !opponentNickname.isEmpty()) {
            return;
        }

        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillRect(0, 0, getWidth(), getHeight());

        String message = "상대 플레이어를 기다리는 중";
        Font font = loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 20);
        g2.setFont(font);

        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(message)) / 2;
        int y = getHeight() / 2;

        g2.setColor(new Color(255, 255, 255, 230));
        g2.fillRoundRect(x - 30, y - 40, fm.stringWidth(message) + 60, 70, 15, 15);
        g2.setColor(PRIMARY);
        g2.drawString(message, x, y);

        String dots = getDots();
        g2.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 24));
        int dotsWidth = g2.getFontMetrics().stringWidth("...");
        g2.drawString(dots, (getWidth() - dotsWidth) / 2, y + 25);
    }

    private String getDots() {
        long time = System.currentTimeMillis() / 500;
        int dotCount = (int) (time % 4);
        return ".".repeat(dotCount);
    }

    private void drawBoard(Graphics2D g2) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();

        int boardPixelSize = CELL_SIZE * BOARD_SIZE;
        int x = (panelWidth - boardPixelSize) / 2;
        int y = (panelHeight - boardPixelSize) / 2;

        g2.setColor(new Color(222, 184, 135));
        g2.fillRect(x, y, boardPixelSize, boardPixelSize);

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));

        for (int i = 0; i <= BOARD_SIZE; i++) {
            g2.drawLine(x, y + i * CELL_SIZE, x + boardPixelSize, y + i * CELL_SIZE);
            g2.drawLine(x + i * CELL_SIZE, y, x + i * CELL_SIZE, y + boardPixelSize);
        }

        int[] dots = {3, 7, 11};
        for (int row : dots) {
            for (int col : dots) {
                int dotX = x + col * CELL_SIZE;
                int dotY = y + row * CELL_SIZE;
                g2.fillOval(dotX - 4, dotY - 4, 8, 8);
            }
        }
    }

    private void drawStones(Graphics2D g2) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int boardPixelSize = CELL_SIZE * BOARD_SIZE;
        int x = (panelWidth - boardPixelSize) / 2;
        int y = (panelHeight - boardPixelSize) / 2;

        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] != 0) {
                    int stoneX = x + j * CELL_SIZE;
                    int stoneY = y + i * CELL_SIZE;
                    int stoneRadius = (int)(CELL_SIZE * 0.4);

                    if (board[i][j] == 1) {
                        g2.setColor(Color.BLACK);
                        g2.fillOval(stoneX - stoneRadius, stoneY - stoneRadius,
                                stoneRadius * 2, stoneRadius * 2);
                        g2.setColor(new Color(50, 50, 50));
                        g2.setStroke(new BasicStroke(2));
                        g2.drawOval(stoneX - stoneRadius, stoneY - stoneRadius,
                                stoneRadius * 2, stoneRadius * 2);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.fillOval(stoneX - stoneRadius, stoneY - stoneRadius,
                                stoneRadius * 2, stoneRadius * 2);
                        g2.setColor(Color.BLACK);
                        g2.setStroke(new BasicStroke(3));
                        g2.drawOval(stoneX - stoneRadius, stoneY - stoneRadius,
                                stoneRadius * 2, stoneRadius * 2);
                    }
                }
            }
        }
    }

    private void handleClick(int mouseX, int mouseY) {
        if (gameOver || !gameEnabled || !myTurn) {
            System.out.println("[OMOK-PANEL] 클릭 무시");
            return;
        }

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int boardPixelSize = CELL_SIZE * BOARD_SIZE;
        int boardX = (panelWidth - boardPixelSize) / 2;
        int boardY = (panelHeight - boardPixelSize) / 2;

        int col = Math.round((mouseX - boardX - CELL_SIZE / 2f) / CELL_SIZE);
        int row = Math.round((mouseY - boardY - CELL_SIZE / 2f) / CELL_SIZE);

        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            return;
        }

        if (board[row][col] != 0) {
            JOptionPane.showMessageDialog(this, "이미 돌이 놓여있습니다!", "오류",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        sendMoveToServer(row, col);
        setMyTurn(false);
        setGameEnabled(false);

        hoverRow = -1;
        hoverCol = -1;

        repaint();
    }

    public void placeStone(int row, int col, int player) {
        board[row][col] = player;
        repaint();
    }

    public void changeTurn() {
        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        System.out.println("[OMOK-PANEL] 턴 변경 → currentPlayer=" + currentPlayer);
    }

    private void sendMoveToServer(int row, int col) {
        if (gameFrame.getClient() != null) {
            String msg = Constants.CMD_GAME_MOVE + " " + row + " " + col;
            gameFrame.getClient().sendMessage(msg);
        }
    }

    // ========== 승리 판정 ==========
    public boolean checkWinAt(int row, int col, int player) {
        return checkDirection(row, col, 0, 1, player) ||
                checkDirection(row, col, 1, 0, player) ||
                checkDirection(row, col, 1, 1, player) ||
                checkDirection(row, col, 1, -1, player);
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
        hoverRow = -1;
        hoverCol = -1;
        updateCursor();
    }

    public void setWinnerColor(int winnerColor) {
        this.winnerColor = winnerColor;
    }

    public void setCurrentPlayer(int player) {
        this.currentPlayer = player;
    }

    private boolean checkDirection(int row, int col, int dRow, int dCol, int player) {
        int count = 1;

        for (int i = 1; i < 5; i++) {
            int r = row + dRow * i;
            int c = col + dCol * i;
            if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && board[r][c] == player) {
                count++;
            } else break;
        }

        for (int i = 1; i < 5; i++) {
            int r = row - dRow * i;
            int c = col - dCol * i;
            if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && board[r][c] == player) {
                count++;
            } else break;
        }

        return count >= 5;
    }

    private void drawGameOverMessage(Graphics2D g2) {
        String winnerName = (winnerColor == gameFrame.getMyColor())
                ? gameFrame.getMyNickname()
                : gameFrame.getOpponentNickname();

        boolean iWon = (winnerColor == gameFrame.getMyColor());
        String message = iWon
                ? "🎉 " + winnerName + "님이 승리했습니다!"
                : "💀 " + winnerName + "님에게 패배했습니다...";

        Font font = new Font("Dialog", Font.BOLD, 30);
        g2.setFont(font);

        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(message);
        int textHeight = fm.getHeight();
        int x = (getWidth() - textWidth) / 2;
        int y = (getHeight() - textHeight) / 2;

        g2.setColor(new Color(255, 255, 255, 210));
        g2.fillRoundRect(x - 25, y - textHeight, textWidth + 50, textHeight + 40, 25, 25);

        g2.setColor(iWon ? new Color(34, 197, 94) : new Color(239, 68, 68));
        g2.drawString(message, x, y + 10);
    }

    public void restart() {
        initBoard();
        myTurn = false;
        hoverRow = -1;
        hoverCol = -1;
        repaint();
    }

    public int getCurrentPlayer() { return currentPlayer; }

    public boolean checkWin() { return gameOver; }

    private Font loadCustomFont(String fontFileName, int style, int size) {
        try {
            String path = "fonts/ttf/" + fontFileName;
            java.io.InputStream fontStream = getClass().getClassLoader()
                    .getResourceAsStream(path);
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
}