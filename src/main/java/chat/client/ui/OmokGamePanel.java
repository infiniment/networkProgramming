package chat.client.ui;

import chat.util.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * OmokGamePanel - 마우스 미리보기 + 반투명 돌 표시
 *
 * 🔧 주요 수정사항:
 *   1. 마우스 hover 시 반투명 돌 미리보기
 *   2. 격자선 하이라이트 효과
 *   3. 동적 커서 변경 (손/기본)
 *   4. 내 턴일 때만 미리보기 표시
 */
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

    // ✅ 마우스 위치 추적용 변수
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
                    System.out.println("[OMOK-PANEL] 클릭 무시: gameEnabled=" + gameEnabled +
                            ", myTurn=" + myTurn);
                    return;
                }
                handleClick(e.getX(), e.getY());
            }

            // ✅ 마우스가 패널을 벗어날 때
            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCol = -1;
                repaint();
            }
        });

        // ✅ 마우스 움직임 추적 추가
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

                // 범위 체크 및 빈 칸 체크
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
        System.out.println("[OMOK-PANEL] setGameEnabled(" + enabled + ")");
        updateCursor();
        repaint();
    }

    public void setOpponentNickname(String opponentNickname) {
        this.opponentNickname = opponentNickname;
        System.out.println("[OMOK-PANEL] opponentNickname 설정: " + opponentNickname);
        repaint();
    }

    public void setMyTurn(boolean myTurn) {
        this.myTurn = myTurn;
        System.out.println("[OMOK-PANEL] setMyTurn(" + myTurn + ")");
        updateCursor();
    }

    public boolean isMyTurn() {
        return myTurn;
    }

    // ✅ 커서 업데이트 메서드
    private void updateCursor() {
        if (gameEnabled && myTurn && !gameOver) {
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    // ========== 렌더링 ==========
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBoard(g2);
        drawStones(g2);

        // ✅ 미리보기 그리기 (돌보다 나중에 그려야 위에 표시됨)
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

    // ✅ 미리보기 그리기 메서드 (격자선 하이라이트 + 반투명 돌)
    private void drawPreview(Graphics2D g2) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int boardPixelSize = CELL_SIZE * BOARD_SIZE;
        int x = (panelWidth - boardPixelSize) / 2;
        int y = (panelHeight - boardPixelSize) / 2;

        int cellX = x + hoverCol * CELL_SIZE;
        int cellY = y + hoverRow * CELL_SIZE;
        int stoneRadius = (int)(CELL_SIZE * 0.4);

        // 1️⃣ 격자선 하이라이트 (밝은 주황색)
        g2.setColor(new Color(255, 200, 100, 80));
        g2.setStroke(new BasicStroke(3));

        // 가로선 하이라이트
        g2.drawLine(x, cellY, x + boardPixelSize, cellY);
        // 세로선 하이라이트
        g2.drawLine(cellX, y, cellX, y + boardPixelSize);

        // 2️⃣ 교차점 원 (작은 원)
        g2.setColor(new Color(255, 159, 64, 120));
        g2.fillOval(cellX - 6, cellY - 6, 12, 12);

        // 3️⃣ 돌 미리보기 (반투명)
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

    // ========== 보드 그리기 ==========
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

        // 화점
        int[] dots = {3, 7, 11};
        for (int row : dots) {
            for (int col : dots) {
                int dotX = x + col * CELL_SIZE;
                int dotY = y + row * CELL_SIZE;
                g2.fillOval(dotX - 4, dotY - 4, 8, 8);
            }
        }
    }

    // ========== 돌 그리기 ==========
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

    // ========== 마우스 클릭 처리 ==========
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

        // 서버에만 전송
        sendMoveToServer(row, col);

        // 내 턴 비활성화 (서버 응답 대기)
        setMyTurn(false);
        setGameEnabled(false);

        // ✅ 미리보기 제거
        hoverRow = -1;
        hoverCol = -1;

        // 화면 갱신
        repaint();
    }

    // ========== 돌 놓기 ==========
    public void placeStone(int row, int col, int player) {
        board[row][col] = player;
        repaint();
    }

    // ========== 턴 변경 ==========
    public void changeTurn() {
        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        System.out.println("[OMOK-PANEL] 턴 변경 → currentPlayer=" + currentPlayer);
    }

    // ========== 서버에 이동 전송 ==========
    private void sendMoveToServer(int row, int col) {
        if (gameFrame.getClient() != null) {
            String msg = Constants.CMD_GAME_MOVE + " " + row + " " + col;
            System.out.println("[OMOK-PANEL] 📤 서버에 이동 전송: " + msg);
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
        // ✅ 게임 종료 시 미리보기 제거
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

    // ========== 게임 오버 메시지 ==========
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

    // ========== Public 메서드 ==========
    public void restart() {
        initBoard();
        myTurn = false;
        hoverRow = -1;  // ✅ 미리보기 초기화
        hoverCol = -1;
        repaint();
    }

    public int getCurrentPlayer() { return currentPlayer; }

    public boolean checkWin() { return gameOver; }

    // ========== 폰트 로드 ==========
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