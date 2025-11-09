package chat.server;

import chat.util.Constants;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OmokGameManager - 오목 게임 관리
 * 🔧 핵심: initiateGameStart()에서 호스트 닉네임을 정확히 전송!
 */
public class OmokGameManager {

    private static final long SESSION_TIMEOUT = 5 * 60 * 1000;
    private static final Object GLOBAL_LOCK = new Object();

    private final Map<String, String> waitQueue = new ConcurrentHashMap<>();
    private final Map<String, OmokGameSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, OmokGameSession> playerToSession = new ConcurrentHashMap<>();

    private final ChatServer server;

    public enum GameState {
        WAITING("대기 중"),
        PLAYING("진행 중"),
        FINISHED("종료됨"),
        ABANDONED("포기됨");

        public final String description;
        GameState(String description) {
            this.description = description;
        }
    }

    public enum GameJoinResult {
        WAITING("첫 번째 플레이어, 대기 중"),
        GAME_STARTED("게임 시작"),
        HOST_NOT_FOUND("호스트 연결 끊김"),
        ALREADY_IN_GAME("이미 게임 진행 중"),
        ERROR("오류 발생");

        public final String message;
        GameJoinResult(String message) {
            this.message = message;
        }
    }

    public OmokGameManager(ChatServer server) {
        this.server = server;
        startCleanupTask();
    }

    // ========== 플레이어 참여 ==========
    public GameJoinResult handlePlayerJoin(String playerNickname, ClientHandler handler) {
        synchronized (GLOBAL_LOCK) {
            System.out.println("[GAME] " + playerNickname + "님이 게임 참여 요청");
            System.out.println("[GAME] 현재 대기열 크기: " + waitQueue.size());

            if (playerToSession.containsKey(playerNickname)) {
                return GameJoinResult.ALREADY_IN_GAME;
            }

            if (waitQueue.isEmpty()) {
                // ========== 호스트 대기 ==========
                waitQueue.put(playerNickname, playerNickname);
                System.out.println("[GAME] ✅ " + playerNickname + "님이 호스트로 대기 시작");
                return GameJoinResult.WAITING;
            } else {
                // ========== 게스트 참여 → 게임 시작! ==========
                String hostNickname = waitQueue.keySet().iterator().next();
                waitQueue.remove(hostNickname);

                System.out.println("[GAME] 🎮 게임 매칭: " + hostNickname + " vs " + playerNickname);

                ClientHandler hostHandler = server.getSession(hostNickname);
                if (hostHandler == null) {
                    System.err.println("[GAME] ❌ 호스트 핸들러를 찾을 수 없음");
                    return GameJoinResult.HOST_NOT_FOUND;
                }

                // 게임 세션 생성
                OmokGameSession session = new OmokGameSession(
                        hostNickname, playerNickname, hostHandler, handler
                );

                String sessionId = session.getSessionId();
                activeSessions.put(sessionId, session);
                playerToSession.put(hostNickname, session);
                playerToSession.put(playerNickname, session);

                System.out.println("[GAME] 📊 세션 생성: " + sessionId);

                // 🔧 즉시 게임 시작 메시지 전송!
                initiateGameStart(hostNickname, session);

                return GameJoinResult.GAME_STARTED;
            }
        }
    }

    // ========== 게임 시작 메시지 전송 (핵심!) ==========
    public void initiateGameStart(String hostNickname, OmokGameSession session) {
        synchronized (GLOBAL_LOCK) {
            System.out.println("[GAME] 📤 게임 시작 프로세스");
            System.out.println("[GAME] 호스트: " + session.host + ", 게스트: " + session.opponent);

            try {
                // 1️⃣ 게스트에게: @game:start <호스트 닉네임>
                String guestMsg = Constants.RESPONSE_GAME_START + " " + session.host;
                System.out.println("[GAME] 📤 게스트 ← " + guestMsg);
                session.opponentHandler.sendMessage(guestMsg);
                session.opponentHandler.outWriter().flush();

                Thread.sleep(50);

                // 2️⃣ 호스트에게: @game:start <게스트 닉네임>
                String hostMsg = Constants.RESPONSE_GAME_START + " " + session.opponent;
                System.out.println("[GAME] 📤 호스트 ← " + hostMsg);
                session.hostHandler.sendMessage(hostMsg);
                session.hostHandler.outWriter().flush();

                Thread.sleep(50);

                // 3️⃣ 게스트 턴 정보
                session.opponentHandler.sendMessage("@game:turn 2");
                session.opponentHandler.outWriter().flush();
                System.out.println("[GAME] 📤 게스트 ← @game:turn 2");

                Thread.sleep(50);

                // 4️⃣ 호스트 턴 정보
                session.hostHandler.sendMessage("@game:turn 1");
                session.hostHandler.outWriter().flush();
                System.out.println("[GAME] 📤 호스트 ← @game:turn 1");

                // 게임 상태 변경
                session.setState(GameState.PLAYING);

                System.out.println("[GAME] ✅✅ 게임 시작 완료!");

            } catch (InterruptedException e) {
                System.err.println("[GAME] ❌ 인터럽트: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========== 게임 이동 기록 ==========
    public synchronized boolean recordMoveWithValidation(String playerNickname, int row, int col) {
        System.out.println("[GAME] 📍 이동: " + playerNickname + " (" + row + ", " + col + ")");

        OmokGameSession session = playerToSession.get(playerNickname);
        if (session == null) {
            System.err.println("[GAME] ❌ 세션 없음");
            return false;
        }

        return session.recordMoveWithValidation(playerNickname, row, col);
    }

    // ========== 게임 종료 ==========
    public synchronized void endGame(String winnerNickname) {
        OmokGameSession session = playerToSession.get(winnerNickname);
        if (session != null) {
            String endMsg = Constants.RESPONSE_GAME_END + " " + winnerNickname;
            session.hostHandler.sendMessage(endMsg);
            session.opponentHandler.sendMessage(endMsg);
            session.setState(GameState.FINISHED);
            System.out.println("[GAME] 🏆 게임 종료: " + winnerNickname + "님 승리");
        }
    }

    // ========== 플레이어 연결 해제 ==========
    public synchronized void handlePlayerDisconnect(String playerNickname) {
        System.out.println("[GAME] 🔌 플레이어 연결 해제: " + playerNickname);

        waitQueue.remove(playerNickname);

        OmokGameSession session = playerToSession.remove(playerNickname);
        if (session != null) {
            session.abandonGame();
            activeSessions.remove(session.getSessionId());

            if (session.host.equals(playerNickname)) {
                session.opponentHandler.sendMessage("[System] 상대방이 연결을 종료했습니다.");
            } else {
                session.hostHandler.sendMessage("[System] 상대방이 연결을 종료했습니다.");
            }

            System.out.println("[GAME] 🗑️ 세션 삭제: " + session.getSessionId());
        }
    }

    // ========== 타임아웃 정리 ==========
    public void cleanupExpiredSessions() {
        List<String> expiredKeys = new ArrayList<>();

        for (Map.Entry<String, OmokGameSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().isExpired(SESSION_TIMEOUT)) {
                expiredKeys.add(entry.getKey());
            }
        }

        synchronized (GLOBAL_LOCK) {
            for (String key : expiredKeys) {
                OmokGameSession session = activeSessions.remove(key);
                if (session != null) {
                    System.out.println("[GAME] ⏱️ 타임아웃: " + key);
                    session.abandonGame();
                }
            }
        }
    }

    private void startCleanupTask() {
        Timer timer = new Timer("OmokGameCleanupTask", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }, 60000, 60000);
    }

    // ========== 세션 조회 ==========
    public OmokGameSession getSessionByPlayer(String playerNickname) {
        return playerToSession.get(playerNickname);
    }

    public int getActiveGameCount() {
        return activeSessions.size();
    }

    public int getWaitingPlayerCount() {
        return waitQueue.size();
    }

    // ========== OmokGameSession 내부 클래스 ==========
    public static class OmokGameSession {
        private final String host;
        private final String opponent;
        private final ClientHandler hostHandler;
        private final ClientHandler opponentHandler;
        private final String sessionId;
        private final long createdAt;

        private GameState state = GameState.WAITING;
        private int currentTurn = 1;

        public OmokGameSession(String host, String opponent,
                               ClientHandler hostHandler,
                               ClientHandler opponentHandler) {
            this.host = host;
            this.opponent = opponent;
            this.hostHandler = hostHandler;
            this.opponentHandler = opponentHandler;
            this.sessionId = UUID.randomUUID().toString();
            this.createdAt = System.currentTimeMillis();
        }

        public synchronized boolean recordMoveWithValidation(String player, int row, int col) {
            if (state != GameState.PLAYING) {
                System.err.println("[GAME] ❌ 게임이 진행 중이 아님");
                return false;
            }

            if (!host.equals(player) && !opponent.equals(player)) {
                System.err.println("[GAME] ❌ 플레이어 아님");
                return false;
            }

            int playerColor = host.equals(player) ? 1 : 2;
            if (playerColor != currentTurn) {
                System.err.println("[GAME] ❌ 순서 아님: 예상 " + currentTurn + ", 시도 " + playerColor);
                return false;
            }

            if (row < 0 || row >= 15 || col < 0 || col >= 15) {
                System.err.println("[GAME] ❌ 범위 오류");
                return false;
            }

            String moveMessage = String.format("@game:move %d %d %d", row, col, playerColor);

            try {
                hostHandler.sendMessage(moveMessage);
                hostHandler.outWriter().flush();

                opponentHandler.sendMessage(moveMessage);
                opponentHandler.outWriter().flush();

                System.out.println("[GAME] ✅ 이동 전파: " + player);

                currentTurn = (currentTurn == 1) ? 2 : 1;
                return true;

            } catch (Exception e) {
                System.err.println("[GAME] ❌ 전송 오류: " + e.getMessage());
                return false;
            }
        }

        public synchronized void abandonGame() {
            state = GameState.ABANDONED;
            System.out.println("[GAME] 🏁 게임 포기: " + sessionId);
        }

        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - createdAt > timeoutMs;
        }

        public void setState(GameState newState) {
            this.state = newState;
            System.out.println("[GAME] 🔄 상태 변경: " + state.description);
        }

        public String getSessionId() { return sessionId; }
        public String getHost() { return host; }
        public String getOpponent() { return opponent; }
        public GameState getState() { return state; }
    }
}