package chat.server;

import chat.util.Constants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BR31GameManager - 베스킨라빈스31 게임 관리
 * 3-5명 멀티플레이어 게임 지원
 *
 * 핵심 흐름:
 * 1. 첫 참여자(호스트) → 인원 설정 대기
 * 2. 호스트가 인원 설정 → 대기 시작
 * 3. 게스트 참여 → 인원 충족 시 게임 시작
 */
public class BR31GameManager {

    private static final long SESSION_TIMEOUT = 10 * 60 * 1000; // 10분
    private static final Object GLOBAL_LOCK = new Object();

    // 방별 대기 세션 (roomId -> session)
    private final Map<String, BR31GameSession> waitingSessions = new ConcurrentHashMap<>();

    // 활성 게임 세션 (sessionId -> session)
    private final Map<String, BR31GameSession> activeSessions = new ConcurrentHashMap<>();

    // 플레이어 -> 세션 매핑
    private final Map<String, BR31GameSession> playerToSession = new ConcurrentHashMap<>();

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

    public enum JoinResult {
        HOST_WAITING("호스트 대기 중"),
        GUEST_JOINED("게스트 참여"),
        GAME_STARTED("게임 시작"),
        ALREADY_IN_GAME("이미 게임 중"),
        ROOM_FULL("방이 꽉 참"),
        ERROR("오류 발생");

        public final String message;
        JoinResult(String message) {
            this.message = message;
        }
    }

    public BR31GameManager(ChatServer server) {
        this.server = server;
        startCleanupTask();
    }

    // ========== 플레이어 참여 ==========
    public JoinResult handlePlayerJoin(String playerNickname, String roomId, ClientHandler handler) {
        synchronized (GLOBAL_LOCK) {
            System.out.println("[BR31] " + playerNickname + "님이 게임 참여 요청 (방: " + roomId + ")");

            // 이미 게임 중인지 확인
            if (playerToSession.containsKey(playerNickname)) {
                return JoinResult.ALREADY_IN_GAME;
            }

            BR31GameSession session = waitingSessions.get(roomId);

            if (session == null) {
                // ========== 호스트 - 새 세션 생성 ==========
                session = new BR31GameSession(roomId, playerNickname, handler);
                waitingSessions.put(roomId, session);

                System.out.println("[BR31] " + playerNickname + "님이 호스트로 대기 시작");

                // 호스트에게 알림
                handler.sendMessage(Constants.RESPONSE_GAME_WAITING + " br31 host");

                return JoinResult.HOST_WAITING;

            } else {
                // ========== 게스트 - 기존 세션에 참여 ==========

                // 인원이 설정되지 않았으면 대기
                if (session.getMaxPlayers() == 0) {
                    System.out.println("[BR31] 호스트가 아직 인원을 설정하지 않음");
                    handler.sendMessage("[System] 호스트가 인원을 설정 중입니다...");
                    return JoinResult.HOST_WAITING;
                }

                // 방이 꽉 찼는지 확인
                if (session.getPlayerCount() >= session.getMaxPlayers()) {
                    System.out.println("[BR31] 방이 꽉 참 (" + session.getMaxPlayers() + "명)");
                    return JoinResult.ROOM_FULL;
                }

                // 플레이어 추가
                session.addPlayer(playerNickname, handler);

                int current = session.getPlayerCount();
                int max = session.getMaxPlayers();

                System.out.println("[BR31] 게스트 참여: " + playerNickname + " (" + current + "/" + max + ")");

                // 모든 대기자에게 현재 상태 알림
                String waitMsg = Constants.RESPONSE_GAME_WAITING + " br31 " + current + "/" + max;
                session.broadcastToAll(waitMsg);

                // 인원이 다 찼는지 확인
                if (current >= max) {
                    // 게임 시작!
                    startGame(session);
                    return JoinResult.GAME_STARTED;
                }

                return JoinResult.GUEST_JOINED;
            }
        }
    }

    // ========== 호스트 인원 설정 ==========
    public void handleHostSetup(String playerNickname, String roomId, int maxPlayers) {
        synchronized (GLOBAL_LOCK) {
            BR31GameSession session = waitingSessions.get(roomId);

            if (session == null) {
                System.err.println("[BR31] 세션을 찾을 수 없음: " + roomId);
                return;
            }

            if (!session.isHost(playerNickname)) {
                System.err.println("[BR31] 호스트가 아님: " + playerNickname);
                return;
            }

            if (maxPlayers < 3 || maxPlayers > 5) {
                System.err.println("[BR31] 잘못된 인원 수: " + maxPlayers);
                return;
            }

            session.setMaxPlayers(maxPlayers);

            System.out.println("[BR31] 호스트 " + playerNickname + "가 최대 인원 " + maxPlayers + "명 설정");

            // 호스트에게 대기 상태 알림
            int current = session.getPlayerCount();
            String waitMsg = Constants.RESPONSE_GAME_WAITING + " br31 " + current + "/" + maxPlayers;
            session.broadcastToAll(waitMsg);
        }
    }

    // ========== 게임 시작 ==========
    private void startGame(BR31GameSession session) {
        System.out.println("[BR31] 게임 시작 준비");

        // 대기 큐에서 제거
        waitingSessions.remove(session.getRoomId());

        // 활성 게임으로 이동
        String sessionId = session.getSessionId();
        activeSessions.put(sessionId, session);

        // 플레이어 매핑
        for (String player : session.getPlayers()) {
            playerToSession.put(player, session);
        }

        // 게임 상태 변경
        session.setState(GameState.PLAYING);

        // 게임 시작 알림 (플레이어 순서 = 턴 순서)
        String players = String.join(",", session.getPlayers());
        String startMsg = Constants.RESPONSE_GAME_START + " br31 " + players;
        session.broadcastToAll(startMsg);

        System.out.println("[BR31] 게임 시작: " + players);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 첫 턴 플레이어 알림
        String firstPlayer = session.getPlayers().get(0);
        session.broadcastToAll("@game:turn " + firstPlayer);

        System.out.println("[BR31] 첫 턴: " + firstPlayer);
    }

    // ========== 게임 이동 처리 ==========
    public boolean handlePlayerMove(String playerNickname, int[] numbers) {
        synchronized (GLOBAL_LOCK) {
            BR31GameSession session = playerToSession.get(playerNickname);

            if (session == null) {
                System.err.println("[BR31] 세션을 찾을 수 없음: " + playerNickname);
                return false;
            }

            if (session.getState() != GameState.PLAYING) {
                System.err.println("[BR31] 게임이 진행 중이 아님");
                return false;
            }

            // 턴 확인
            String currentTurnPlayer = session.getCurrentTurnPlayer();
            if (!currentTurnPlayer.equals(playerNickname)) {
                System.err.println("[BR31] 현재 턴이 아님: " + playerNickname);
                return false;
            }

            // 숫자 유효성 검사
            if (numbers.length < 1 || numbers.length > 3) {
                System.err.println("[BR31] 잘못된 숫자 개수: " + numbers.length);
                return false;
            }

            int currentCount = session.getCurrentCount();

            // 연속된 숫자인지 확인
            for (int i = 0; i < numbers.length; i++) {
                if (numbers[i] != currentCount + i + 1) {
                    System.err.println("[BR31] 연속되지 않은 숫자");
                    return false;
                }
            }

            // 마지막 숫자로 카운트 업데이트
            int newCount = numbers[numbers.length - 1];
            session.setCurrentCount(newCount);

            System.out.println("[BR31] " + playerNickname + " → " + Arrays.toString(numbers) + " (현재: " + newCount + ")");

            // 모든 플레이어에게 업데이트 전송
            String numbersStr = Arrays.toString(numbers).replaceAll("[\\[\\] ]", "");
            String updateMsg = "@game:update " + newCount + " " + playerNickname + " " + numbersStr;
            session.broadcastToAll(updateMsg);

            // 31이면 게임 종료
            if (newCount >= 31) {
                endGame(session, playerNickname);
                return true;
            }

            // 다음 턴
            session.nextTurn();
            String nextPlayer = session.getCurrentTurnPlayer();
            session.broadcastToAll("@game:turn " + nextPlayer);

            System.out.println("[BR31] 다음 턴: " + nextPlayer);

            return true;
        }
    }

    // ========== 게임 종료 ==========
    private void endGame(BR31GameSession session, String loser) {
        System.out.println("[BR31] 🏁 게임 종료 - 패자: " + loser);

        session.setState(GameState.FINISHED);

        String endMsg = Constants.RESPONSE_GAME_END + " br31 loser=" + loser;
        session.broadcastToAll(endMsg);

        // 세션 정리
        cleanupSession(session);
    }

    // ========== 세션 정리 ==========
    private void cleanupSession(BR31GameSession session) {
        activeSessions.remove(session.getSessionId());

        for (String player : session.getPlayers()) {
            playerToSession.remove(player);
        }

        System.out.println("[BR31] 세션 정리 완료: " + session.getSessionId());
    }

    // ========== 플레이어 연결 해제 ==========
    public void handlePlayerDisconnect(String playerNickname) {
        synchronized (GLOBAL_LOCK) {
            System.out.println("[BR31] 플레이어 연결 해제: " + playerNickname);

            // 대기 세션에서 제거
            for (BR31GameSession session : waitingSessions.values()) {
                if (session.hasPlayer(playerNickname)) {
                    if (session.isHost(playerNickname)) {
                        // 호스트가 나가면 세션 삭제
                        waitingSessions.remove(session.getRoomId());
                        System.out.println("[BR31] 호스트 이탈 - 세션 삭제");
                    } else {
                        // 게스트가 나가면 플레이어만 제거
                        session.removePlayer(playerNickname);

                        // 나머지 대기자에게 알림
                        int current = session.getPlayerCount();
                        int max = session.getMaxPlayers();
                        if (max > 0) {
                            String waitMsg = Constants.RESPONSE_GAME_WAITING + " br31 " + current + "/" + max;
                            session.broadcastToAll(waitMsg);
                        }
                    }
                    return;
                }
            }

            // 활성 게임에서 제거
            BR31GameSession session = playerToSession.remove(playerNickname);
            if (session != null) {
                session.setState(GameState.ABANDONED);
                session.broadcastToAll("[System] " + playerNickname + "님이 게임을 포기했습니다.");
                cleanupSession(session);
            }
        }
    }

    // ========== 타임아웃 정리 ==========
    public void cleanupExpiredSessions() {
        List<String> expiredKeys = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, BR31GameSession> entry : waitingSessions.entrySet()) {
            if (now - entry.getValue().getCreatedAt() > SESSION_TIMEOUT) {
                expiredKeys.add(entry.getKey());
            }
        }

        synchronized (GLOBAL_LOCK) {
            for (String key : expiredKeys) {
                BR31GameSession session = waitingSessions.remove(key);
                if (session != null) {
                    System.out.println("[BR31]]️ 대기 세션 타임아웃: " + key);
                    session.broadcastToAll("[System] 대기 시간 초과로 게임이 취소되었습니다.");
                }
            }
        }
    }

    private void startCleanupTask() {
        Timer timer = new Timer("BR31GameCleanupTask", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }, 60000, 60000);
    }

    // ========== 조회 메서드 ==========
    public BR31GameSession getSessionByPlayer(String playerNickname) {
        return playerToSession.get(playerNickname);
    }

    public int getActiveGameCount() {
        return activeSessions.size();
    }

    public int getWaitingSessionCount() {
        return waitingSessions.size();
    }

    // ========== BR31GameSession 내부 클래스 ==========
    public static class BR31GameSession {
        private final String roomId;
        private final String sessionId;
        private final String hostNickname;
        private final List<String> players;
        private final List<ClientHandler> handlers;
        private final long createdAt;

        private GameState state = GameState.WAITING;
        private int maxPlayers = 0;
        private int currentCount = 0;
        private int currentTurnIndex = 0;

        public BR31GameSession(String roomId, String hostNickname, ClientHandler hostHandler) {
            this.roomId = roomId;
            this.sessionId = UUID.randomUUID().toString();
            this.hostNickname = hostNickname;
            this.players = new ArrayList<>();
            this.handlers = new ArrayList<>();
            this.createdAt = System.currentTimeMillis();

            this.players.add(hostNickname);
            this.handlers.add(hostHandler);
        }

        public void addPlayer(String nickname, ClientHandler handler) {
            if (players.size() < maxPlayers) {
                players.add(nickname);
                handlers.add(handler);
            }
        }

        public void removePlayer(String nickname) {
            int index = players.indexOf(nickname);
            if (index >= 0) {
                players.remove(index);
                handlers.remove(index);
            }
        }

        public void broadcastToAll(String message) {
            for (ClientHandler handler : handlers) {
                handler.sendMessage(message);
            }
        }

        public void nextTurn() {
            currentTurnIndex = (currentTurnIndex + 1) % players.size();
        }

        public String getCurrentTurnPlayer() {
            if (players.isEmpty()) return null;
            return players.get(currentTurnIndex);
        }

        public boolean isHost(String nickname) {
            return hostNickname.equals(nickname);
        }

        public boolean hasPlayer(String nickname) {
            return players.contains(nickname);
        }

        // Getters & Setters
        public String getRoomId() { return roomId; }
        public String getSessionId() { return sessionId; }
        public List<String> getPlayers() { return new ArrayList<>(players); }
        public int getPlayerCount() { return players.size(); }
        public int getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
        public int getCurrentCount() { return currentCount; }
        public void setCurrentCount(int currentCount) { this.currentCount = currentCount; }
        public GameState getState() { return state; }
        public void setState(GameState state) { this.state = state; }
        public long getCreatedAt() { return createdAt; }
    }
}