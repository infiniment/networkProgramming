package chat.ui.main;

import chat.client.ChatClient;
import chat.shared.model.RoomDto;
import chat.ui.chat.ChatFrame;
import chat.util.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RoomListFrame - 채팅방 목록 화면
 */
public class RoomListFrame extends JFrame implements ChatClient.MessageListener {

    // ========== 색상 팔레트 ==========
    private static final Color PRIMARY = new Color(255, 159, 64);
    private static final Color PRIMARY_HOVER = new Color(255, 140, 40);
    private static final Color BG_COLOR = new Color(255, 247, 237);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(31, 41, 55);
    private static final Color TEXT_SECONDARY = new Color(255, 159, 64);
    private static final Color ACCENT_LIGHT = new Color(254, 215, 170);

    private final String nickname;
    private final String serverLabel;
    private ChatClient client;

    private JLabel lblTotalRooms;
    private JLabel lblOnlineUsers;
    private JLabel lblActiveChats;

    private DefaultListModel<RoomDto> model = new DefaultListModel<>();
    private JList<RoomDto> roomList;
    private JButton btnCreate;
    private JButton btnRefresh;

    private JLabel lblStatusIcon;
    private JLabel lblStatusText;

    private final List<String> passthroughLog = new CopyOnWriteArrayList<>();

    // 🔧 게임 메시지 버퍼
    private List<String> gameMessageBuffer = new CopyOnWriteArrayList<>();

    // 여러 방 한번에 열 수 있게
    private Map<String, ChatFrame> openChatFrames = new HashMap<>();

    public RoomListFrame(String nickname, String serverLabel) {
        this.nickname = nickname;
        this.serverLabel = serverLabel;

        setTitle("멀티룸 채팅 - 채팅방 목록");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(960, 720);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        mainPanel.add(buildHeader(), BorderLayout.NORTH);
        mainPanel.add(buildContent(), BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    // ========== 헤더 영역 ==========
    private JComponent buildHeader() {
        JPanel header = new RoundedPanel(15);
        header.setBackground(CARD_BG);
        header.setBorder(new EmptyBorder(18, 24, 18, 24));
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 70));

        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        JLabel title = new JLabel("📋 채팅방 목록");
        title.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        leftPanel.add(title);

        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        lblStatusIcon = new JLabel(makeStatusIcon(PRIMARY));
        lblStatusText = new JLabel("연결됨");
        lblStatusText.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
        lblStatusText.setForeground(TEXT_PRIMARY);

        JLabel lblUser = new JLabel("👤 " + nickname);
        lblUser.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.BOLD, 13));
        lblUser.setForeground(TEXT_PRIMARY);

        JLabel lblServer = new JLabel("@" + serverLabel);
        lblServer.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        lblServer.setForeground(TEXT_SECONDARY);

        right.add(lblStatusIcon);
        right.add(lblStatusText);
        right.add(Box.createHorizontalStrut(8));
        right.add(lblUser);
        right.add(lblServer);

        rightPanel.add(right);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(header, BorderLayout.CENTER);
        wrapper.setBorder(new EmptyBorder(0, 0, 16, 0));
        return wrapper;
    }

    // ========== 메인 컨텐츠 ==========
    private JComponent buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);

        content.add(buildStats(), BorderLayout.NORTH);
        content.add(buildRoomListPanel(), BorderLayout.CENTER);

        return content;
    }

    // ========== 통계 카드 ==========
    private JComponent buildStats() {
        JPanel stats = new JPanel(new GridLayout(1, 3, 16, 0));
        stats.setOpaque(false);

        lblTotalRooms = new JLabel("0");
        lblOnlineUsers = new JLabel("0");
        lblActiveChats = new JLabel("0");

        stats.add(createStatCard("전체 채팅방", lblTotalRooms));
        stats.add(createStatCard("접속 중인 사용자", lblOnlineUsers));
        stats.add(createStatCard("활성 대화", lblActiveChats));

        return stats;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new RoundedPanel(15);
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(24, 20, 24, 20));
        card.setLayout(new BorderLayout());

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));
        lblTitle.setForeground(new Color(120, 130, 140));

        valueLabel.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 32));
        valueLabel.setForeground(TEXT_PRIMARY);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel valueWrapper = new JPanel(new BorderLayout());
        valueWrapper.setOpaque(false);
        valueWrapper.setBorder(new EmptyBorder(12, 0, 0, 0));
        valueWrapper.add(valueLabel, BorderLayout.CENTER);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueWrapper, BorderLayout.CENTER);

        return card;
    }

    // ========== 방 목록 패널 ==========
    private JComponent buildRoomListPanel() {
        JPanel panel = new RoundedPanel(15);
        panel.setBackground(CARD_BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setLayout(new BorderLayout(0, 12));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel sectionTitle = new JLabel("🎯 활성 채팅방");
        sectionTitle.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        sectionTitle.setForeground(TEXT_PRIMARY);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        btnRefresh = createActionButton("새로고침", false);
        btnRefresh.addActionListener(e -> requestRooms());

        btnCreate = createActionButton("+ 방 만들기", true);
        btnCreate.addActionListener(e -> showCreateDialog());

        actions.add(btnRefresh);
        actions.add(btnCreate);

        top.add(sectionTitle, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        roomList = new JList<>(model);
        roomList.setCellRenderer(new RoomRenderer());
        roomList.setBackground(Color.WHITE);
        roomList.setSelectionBackground(Color.WHITE);
        roomList.setSelectionForeground(TEXT_PRIMARY);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 🔧 셀 더블클릭 시 방 입장
        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = roomList.locationToIndex(e.getPoint());
                if (index < 0) return;

                Rectangle cell = roomList.getCellBounds(index, index);
                if (cell == null || !cell.contains(e.getPoint())) return;

                int relX = e.getX() - cell.x;
                int w = cell.width;

                // 마지막 50px 정도: 삭제 버튼 영역
                if (relX > w - 50) {
                    RoomDto r = model.get(index);
                    int res = JOptionPane.showConfirmDialog(
                            RoomListFrame.this,
                            "'" + r.name + "' 방을 삭제하시겠습니까?",
                            "방 삭제",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (res == JOptionPane.OK_OPTION && client != null) {
                        client.sendMessage(Constants.CMD_ROOM_DELETE + " " + r.name);
                    }
                    return;
                }

                // 그 앞 100px 정도: 입장 버튼 영역
                if (relX > w - 150) {
                    roomList.setSelectedIndex(index);
                    joinSelected();
                    return;
                }

                // 버튼 영역이 아닌 곳 더블클릭 → 입장
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    roomList.setSelectedIndex(index);
                    joinSelected();
                }
            }
        });

        roomList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = roomList.locationToIndex(e.getPoint());
                if (index < 0) {
                    roomList.setCursor(Cursor.getDefaultCursor());
                    return;
                }
                Rectangle cell = roomList.getCellBounds(index, index);
                if (cell == null || !cell.contains(e.getPoint())) {
                    roomList.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                int relX = e.getX() - cell.x;
                int w = cell.width;

                // 입장/삭제 영역 위에 있을 땐 손가락 커서
                if (relX > w - 150) {
                    roomList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    roomList.setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(roomList);
        scroll.setBorder(BorderFactory.createLineBorder(ACCENT_LIGHT, 1));
        scroll.getViewport().setBackground(Color.WHITE);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ========== 상단 버튼 ==========
    private JButton createActionButton(String text, boolean isPrimary) {
        JButton btn = new JButton() {
            private boolean hover = false;
            private String buttonText = text;

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
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                if (isPrimary) {
                    g2.setColor(hover ? PRIMARY_HOVER : PRIMARY);
                } else {
                    g2.setColor(hover ? ACCENT_LIGHT : new Color(247, 249, 252));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                g2.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.BOLD, 13));
                g2.setColor(isPrimary ? Color.WHITE : TEXT_PRIMARY);

                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(buttonText);
                int textHeight = fm.getAscent();

                int x = (getWidth() - textWidth) / 2;
                int y = (getHeight() + textHeight) / 2 - 2;

                g2.drawString(buttonText, x, y);
                g2.dispose();
            }
        };

        btn.setText(text);
        btn.setPreferredSize(new Dimension(120, 38));
        btn.setMinimumSize(new Dimension(120, 38));
        btn.setMaximumSize(new Dimension(120, 38));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(0, 0, 0, 0));
        btn.setOpaque(false);

        return btn;
    }

    // ========== 방 만들기 다이얼로그 ==========
    private void showCreateDialog() {
        JTextField tfName = new JTextField();
        tfName.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));

        JSpinner spCap = new JSpinner(new SpinnerNumberModel(10, 2, 99, 1));
        spCap.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));

        JCheckBox ckLock = new JCheckBox("비밀방 (잠금)");
        ckLock.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));

        JPanel p = new JPanel(new GridLayout(0, 1, 8, 8));
        p.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel lblName = new JLabel("방 이름");
        lblName.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 13));
        JLabel lblCap = new JLabel("정원");
        lblCap.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 13));

        p.add(lblName);
        p.add(tfName);
        p.add(lblCap);
        p.add(spCap);
        p.add(ckLock);

        int ok = JOptionPane.showConfirmDialog(this, p, "새 방 만들기",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (ok == JOptionPane.OK_OPTION) {
            String name = tfName.getText().trim();
            int cap = (Integer) spCap.getValue();
            boolean lock = ckLock.isSelected();
            if (!name.isEmpty() && client != null) {
                client.sendMessage(String.format(Constants.CMD_ROOM_CREATE + " %s %d %s",
                        name, cap, lock ? "lock" : "open"));
                requestRooms();
            }
        }
    }

    // ========== 방 입장 ==========
    private void joinSelected() {
        RoomDto r = roomList.getSelectedValue();
        if (r == null || client == null) return;

        // 이미 열린 방이면 앞으로
        if (openChatFrames.containsKey(r.name)) {
            ChatFrame existingChat = openChatFrames.get(r.name);
            existingChat.toFront();
            existingChat.requestFocus();
            return;
        }

        client.sendMessage(Constants.CMD_JOIN_ROOM + " " + r.name);

        ChatFrame chat = new ChatFrame(nickname, serverLabel + " · " + r.name, this);
        openChatFrames.put(r.name, chat);

        chat.updateMemberCount(r.participants);
        chat.bind(client);

        for (String line : passthroughLog) {
            chat.onMessageReceived(line);
        }
        passthroughLog.clear();

        System.out.println("[RoomListFrame] 📤 게임 버퍼 크기: " + gameMessageBuffer.size());
        for (String gameLine : gameMessageBuffer) {
            System.out.println("[RoomListFrame] 📤 ChatFrame에 전달: " + gameLine);
            chat.onMessageReceived(gameLine);
        }
        gameMessageBuffer.clear();

        chat.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                openChatFrames.remove(r.name);
            }
        });

        chat.setVisible(true);
    }

    // ========== ChatClient 바인딩 ==========
    public void bind(ChatClient client) {
        this.client = client;
        this.client.startReceiving(this);
        requestRooms();
    }

    private void requestRooms() {
        if (client == null) return;
        client.sendMessage(Constants.CMD_ROOMS_LIST);
    }

    // ========== 메시지 수신 ==========
    @Override
    public void onMessageReceived(String line) {
        System.out.println("[RoomListFrame] 수신: " + line);

        // 게임 메시지
        if (line.startsWith("@game:")) {
            gameMessageBuffer.add(line);
            for (ChatFrame frame : openChatFrames.values()) {
                frame.onMessageReceived(line);
            }
            return;
        }

        // 방 리스트 갱신
        if (line.startsWith(Constants.RESPONSE_ROOMS + " ")) {
            String json = line.substring(Constants.RESPONSE_ROOMS.length() + 1).trim();
            List<RoomDto> rooms = parseRooms(json);
            SwingUtilities.invokeLater(() -> applyRooms(rooms));
            return;
        }

        // 시스템 메시지
        if (line.startsWith("[System] ")) {
            String message = line.substring("[System] ".length()).trim();
            System.out.println("[RoomListFrame System] " + message);

            // 🔔 모달 알림 (EDT에서 실행)
            SwingUtilities.invokeLater(() -> {
                int type = (message.contains("실패") || message.contains("권한") || message.contains("없습니다"))
                        ? JOptionPane.WARNING_MESSAGE
                        : JOptionPane.INFORMATION_MESSAGE;

                JOptionPane.showMessageDialog(
                        RoomListFrame.this,
                        message,
                        "시스템 알림",
                        type
                );
            });

            // 열린 모든 ChatFrame에도 그대로 전달 (채팅창에 로그 남기고 싶으면 유지)
            for (ChatFrame frame : openChatFrames.values()) {
                frame.onMessageReceived(line);
            }
            return;
        }

        // 구규격 게임 메시지
        if (line.startsWith("[GAME]")) {
            for (ChatFrame frame : openChatFrames.values()) {
                frame.onMessageReceived(line);
            }
            if (openChatFrames.isEmpty()) {
                gameMessageBuffer.add(line);
            }
            return;
        }

        // 일반 채팅
        for (ChatFrame frame : openChatFrames.values()) {
            frame.onMessageReceived(line);
        }

        if (openChatFrames.isEmpty()) {
            passthroughLog.add(line);
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            if (lblStatusIcon != null) lblStatusIcon.setIcon(makeStatusIcon(Color.RED));
            if (lblStatusText != null) lblStatusText.setText("연결 끊김");
            JOptionPane.showMessageDialog(this, "서버 연결이 끊겼습니다.", "연결 종료",
                    JOptionPane.WARNING_MESSAGE);
            dispose();
        });
    }

    // ========== 방 목록 적용 ==========
    private void applyRooms(List<RoomDto> rooms) {
        model.clear();
        for (RoomDto r : rooms) model.addElement(r);

        lblTotalRooms.setText(String.valueOf(rooms.size()));
        int users = rooms.stream().mapToInt(r -> r.participants).sum();
        lblOnlineUsers.setText(String.valueOf(users));
        long active = rooms.stream().filter(r -> r.active).count();
        lblActiveChats.setText(String.valueOf(active));
    }

    // ========== JSON 파싱 ==========
    private List<RoomDto> parseRooms(String json) {
        try {
            List<RoomDto> out = new ArrayList<>();
            String arr = json.trim();
            if (!arr.startsWith("[") || !arr.endsWith("]")) return out;
            String body = arr.substring(1, arr.length() - 1).trim();
            if (body.isEmpty()) return out;

            int depth = 0;
            int start = 0;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                if (depth == 0 && (i == body.length() - 1 || body.charAt(i + 1) == ',')) {
                    String obj = body.substring(start, i + 1);
                    out.add(parseRoomObject(obj));
                    start = i + 2;
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private RoomDto parseRoomObject(String obj) {
        RoomDto r = new RoomDto("unknown", 0, 0, true, false);
        String s = obj.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        String[] pairs = s.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
        for (String p : pairs) {
            String[] kv = p.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().replaceAll("^\\\"|\\\"$", "");
            String val = kv[1].trim();

            switch (key) {
                case "name" -> r.name = val.replaceAll("^\\\"|\\\"$", "");
                case "participants" -> r.participants = parseInt(val);
                case "capacity" -> r.capacity = parseInt(val);
                case "active" -> r.active = parseBool(val);
                case "locked" -> r.locked = parseBool(val);
            }
        }
        return r;
    }

    private int parseInt(String v) {
        try {
            return Integer.parseInt(v.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean parseBool(String v) {
        return v.trim().startsWith("t") || v.trim().startsWith("T");
    }

    // ========== 유틸리티 ==========
    private Icon makeStatusIcon(Color color) {
        int size = 10;
        return new Icon() {
            public int getIconWidth() {
                return size;
            }

            public int getIconHeight() {
                return size;
            }

            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, size, size);
                g2.setColor(Color.DARK_GRAY);
                g2.drawOval(x, y, size, size);
                g2.dispose();
            }
        };
    }

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

    // ========== 커스텀 컴포넌트 ==========
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

    // ========== 방 목록 렌더러 ==========
    private class RoomRenderer extends JPanel implements ListCellRenderer<RoomDto> {
        private final JLabel icon = new JLabel("💬");
        private final JLabel name = new JLabel();
        private final JLabel sub = new JLabel();
        private final JLabel status = new JLabel("● 활성");
        private final JButton joinBtn;
        private final JButton deleteBtn;   // 🔥 휴지통 버튼

        public RoomRenderer() {
            setLayout(new BorderLayout(16, 8));
            setBorder(new EmptyBorder(14, 18, 14, 18));

            JPanel left = new JPanel(new BorderLayout(10, 0));
            left.setOpaque(false);

            icon.setFont(new Font("Dialog", Font.PLAIN, 22));
            icon.setPreferredSize(new Dimension(30, 30));
            left.add(icon, BorderLayout.WEST);

            JPanel text = new JPanel(new GridLayout(2, 1, 0, 3));
            text.setOpaque(false);

            name.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 15));
            name.setForeground(TEXT_PRIMARY);

            sub.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
            sub.setForeground(new Color(120, 130, 140));

            text.add(name);
            text.add(sub);
            left.add(text, BorderLayout.CENTER);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);

            status.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
            status.setForeground(PRIMARY);
            status.setPreferredSize(new Dimension(50, 20));

            joinBtn   = createSmallButton("입장하기");
            deleteBtn = createIconButton("🗑");

            right.add(status);
            right.add(joinBtn);
            right.add(deleteBtn);

            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        private JButton createSmallButton(String text) {
            JButton btn = new JButton(text) {
                private boolean btnHover = false;
                private boolean btnPressed = false;

                {
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            if (isEnabled()) {
                                btnHover = true;
                                repaint();
                            }
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            btnHover = false;
                            btnPressed = false;
                            repaint();
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (isEnabled()) {
                                btnPressed = true;
                                repaint();
                            }
                        }

                        @Override
                        public void mouseReleased(MouseEvent e) {
                            btnPressed = false;
                            repaint();
                        }

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            e.consume();
                        }
                    });
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (btnPressed) {
                        g2.setColor(new Color(255, 120, 20));
                    } else if (btnHover) {
                        g2.setColor(PRIMARY_HOVER);
                    } else {
                        g2.setColor(PRIMARY);
                    }

                    int offsetY = btnPressed ? 2 : 0;
                    g2.translate(0, offsetY);

                    g2.fillRoundRect(0, 0, getWidth(), getHeight() - (btnPressed ? 2 : 0), 8, 8);

                    g2.dispose();
                    super.paintComponent(g);
                }
            };

            btn.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
            btn.setForeground(Color.WHITE);
            btn.setPreferredSize(new Dimension(90, 32));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setHorizontalAlignment(SwingConstants.CENTER);
            btn.setVerticalAlignment(SwingConstants.CENTER);

            return btn;
        }

        private JButton createIconButton(String iconText) {
            JButton btn = new JButton(iconText) {
                private boolean hover = false;
                private boolean pressed = false;

                {
                    setFont(new Font("Dialog", Font.PLAIN, 16));
                    setMargin(new Insets(0, 0, 0, 0));
                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                        @Override public void mouseExited(MouseEvent e)  { hover = false; pressed = false; repaint(); }
                        @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                        @Override public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
                    });
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (pressed)      g2.setColor(new Color(230, 60, 60));
                    else if (hover)   g2.setColor(new Color(248, 113, 113));
                    else              g2.setColor(new Color(239, 68, 68));

                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                    super.paintComponent(g);
                    g2.dispose();
                }
            };

            btn.setForeground(Color.WHITE);
            btn.setPreferredSize(new Dimension(36, 32));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomDto> list, RoomDto value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            name.setText(value.name + (value.locked ? " 🔒" : ""));
            sub.setText(value.toCounter());

            status.setText(value.active ? "● 활성" : "○ 비활성");
            status.setForeground(value.active ? PRIMARY : new Color(120, 130, 140));

            // 입장 버튼 액션 재설정
            for (ActionListener al : joinBtn.getActionListeners()) {
                joinBtn.removeActionListener(al);
            }
            joinBtn.addActionListener(e -> {
                roomList.setSelectedIndex(index);
                joinSelected();
            });

            // 삭제 버튼 액션 재설정
            for (ActionListener al : deleteBtn.getActionListeners()) {
                deleteBtn.removeActionListener(al);
            }
            deleteBtn.setToolTipText("방 삭제");
            deleteBtn.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(
                        RoomListFrame.this,
                        "'" + value.name + "' 방을 삭제하시겠습니까?",
                        "방 삭제",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (res == JOptionPane.OK_OPTION && client != null) {
                    client.sendMessage(Constants.CMD_ROOM_DELETE + " " + value.name);
                }
            });

            if (isSelected) {
                setBackground(ACCENT_LIGHT);
            } else {
                setBackground(index % 2 == 0 ? Color.WHITE : new Color(252, 252, 252));
            }

            setOpaque(true);
            return this;
        }
    }
}
