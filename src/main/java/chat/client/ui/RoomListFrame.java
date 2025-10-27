package chat.client.ui;

import chat.client.ChatClient;
import chat.model.RoomDto;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.Timer;
import javax.swing.SpinnerNumberModel;
import javax.swing.AbstractAction;


public class RoomListFrame extends JFrame implements ChatClient.MessageListener  {
    private final String nickname;
    private final String serverLabel;
    private ChatClient client;

    private JLabel lblTotalRooms;
    private JLabel lblOnlineUsers;
    private JLabel lblActiveChats;

    private DefaultListModel<RoomDto> model = new DefaultListModel<>();
    private JList<RoomDto> roomList;
    private JButton btnCreate;

    private JLabel lblStatusIcon;
    private JLabel lblStatusText;

    // 다른 화면으로 리스너 넘길 때를 대비해 메시지 버퍼링
    private final List<String> passthroughLog = new CopyOnWriteArrayList<>();

    public RoomListFrame(String nickname, String serverLabel) {
        this.nickname = nickname;
        this.serverLabel = serverLabel;

        setTitle("멀티룸 채팅 - 채팅방 목록");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(920, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildStats(), BorderLayout.CENTER);
        add(buildListPanel(), BorderLayout.SOUTH);
    }

    private JComponent buildHeader() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(12, 16, 8, 16));

        // (1) 왼쪽 - 로고 + 타이틀
        ImageIcon logoIcon = new ImageIcon(getClass().getResource("/images/logo.png"));
        Image img = logoIcon.getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH);
        logoIcon = new ImageIcon(img);
        JLabel brand = new JLabel("멀티룸 채팅", logoIcon, SwingConstants.LEFT);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 18f));
        brand.setIconTextGap(8);

        // (2) 오른쪽 - 연결 상태 + 유저 정보
        lblStatusIcon = new JLabel(makeStatusIcon(Color.GREEN));
        lblStatusText = new JLabel("연결됨");
        JLabel lblUser = new JLabel("👤 " + nickname + "   @" + serverLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(lblStatusIcon);
        right.add(lblStatusText);
        right.add(lblUser);

        top.add(brand, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);

        return top;
    }

    //  /** 채팅방 리스트 화면에서 원형 상태 표시 아이콘 (초록: 연결됨 / 빨강: 끊김) */
    private Icon makeStatusIcon(Color color) {
        int size = 10;
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillOval(x, y, size, size);
                g.setColor(Color.DARK_GRAY);
                g.drawOval(x, y, size, size);
            }
        };
    }

    private JComponent buildStats() {
        JPanel p = new JPanel(new GridLayout(1, 3, 12, 12));
        p.setBorder(new EmptyBorder(6, 16, 6, 16));

        lblTotalRooms  = statCard("전체 채팅방", "0");
        lblOnlineUsers = statCard("접속 중인 사용자", "0");
        lblActiveChats = statCard("활성 대화", "0");

        p.add(wrap(lblTotalRooms));
        p.add(wrap(lblOnlineUsers));
        p.add(wrap(lblActiveChats));
        return p;
    }

    private static JPanel wrap(JComponent c) {
        JPanel w = new JPanel(new BorderLayout());
        w.add(c, BorderLayout.CENTER);
        return w;
    }

    private JLabel statCard(String title, String value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        card.setBackground(new Color(247, 249, 252));
        card.setOpaque(true);

        JLabel t = new JLabel(title);
        t.setForeground(new Color(110, 120, 130));

        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 26f));

        card.add(t, BorderLayout.NORTH);
        card.add(v, BorderLayout.CENTER);

        JLabel holder = new JLabel();
        holder.setLayout(new BorderLayout());
        holder.add(card, BorderLayout.CENTER);
        return v;
    }

    private JComponent buildListPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(new EmptyBorder(8, 16, 16, 16));

        JLabel section = new JLabel("활성 채팅방");
        section.setBorder(new EmptyBorder(8, 0, 8, 0));
        container.add(section, BorderLayout.NORTH);

        roomList = new JList<>(model);
        roomList.setCellRenderer(new RoomRenderer());
        JScrollPane sp = new JScrollPane(roomList);
        container.add(sp, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnCreate = new JButton("+ 방 만들기");
        btnCreate.addActionListener(e -> showCreateDialog());
        JButton btnRefresh = new JButton("새로고침");
        btnRefresh.addActionListener(e -> requestRooms());
        actions.add(btnRefresh);
        actions.add(btnCreate);

        container.add(actions, BorderLayout.SOUTH);
        return container;
    }

    /** ChatClient 바인딩 후 방 목록 요청 */
    public void bind(ChatClient client) {
        this.client = client;
        this.client.startReceiving(this);
        requestRooms();
    }

    /** 서버에 방 목록 요청 */
    private void requestRooms() {
        if (client == null) return;
        client.sendMessage("/rooms");
        // 500ms 내 응답이 없으면 목업 표시(서버 미구현 시 데모용)
        new Timer(600, e -> {
            if (model.isEmpty()) fillMockRooms();
            ((Timer)e.getSource()).stop();
        }).start();
    }

    /** 방 만들기 다이얼로그 */
    private void showCreateDialog() {
        JTextField tfName = new JTextField();
        JSpinner spCap = new JSpinner(new SpinnerNumberModel(10, 2, 99, 1));
        JCheckBox ckLock = new JCheckBox("비밀방(잠금)");

        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        p.add(new JLabel("방 이름"));
        p.add(tfName);
        p.add(new JLabel("정원"));
        p.add(spCap);
        p.add(ckLock);

        int ok = JOptionPane.showConfirmDialog(this, p, "새 방 만들기",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            String name = tfName.getText().trim();
            int cap = (Integer) spCap.getValue();
            boolean lock = ckLock.isSelected();
            if (!name.isEmpty() && client != null) {
                client.sendMessage(String.format("/room.create %s %d %s",
                        name, cap, lock ? "lock" : "open"));
                // 만든 직후 목록 재요청
                requestRooms();
            }
        }
    }

    /** 항목 더블클릭 또는 버튼으로 입장 */
    private void joinSelected() {
        RoomDto r = roomList.getSelectedValue();
        if (r == null || client == null) return;
        client.sendMessage("/join " + r.name);

        // 채팅 화면으로 전환
        ChatFrame chat = new ChatFrame(nickname, serverLabel + " · " + r.name);
        chat.bind(client);
        // 방 전환 전 받은 남은 메시지가 있다면 전달
        for (String line : passthroughLog) chat.onMessageReceived(line);
        passthroughLog.clear();

        chat.setVisible(true);
        dispose();
    }

    /** 수신 콜백 */
    @Override public void onMessageReceived(String line) {
        // 방 목록 응답: "@rooms <json>"
        if (line.startsWith("@rooms ")) {
            String json = line.substring(8).trim();
            List<RoomDto> rooms = parseRooms(json);
            SwingUtilities.invokeLater(() -> applyRooms(rooms));
            return;
        }

        // UI 상에서는 로그를 쌓아두었다가 실제 입장 후 ChatFrame으로 넘김
        passthroughLog.add(line);

        // 방 목록 화면에서 보여줄 필요는 없지만, 필요하면 아래처럼 상태 표시 가능:
        // System.out.println("[buffered] " + line);
    }


    @Override public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            if (lblStatusIcon != null) lblStatusIcon.setIcon(makeStatusIcon(Color.RED));
            if (lblStatusText != null) lblStatusText.setText("연결 끊김");
            JOptionPane.showMessageDialog(this, "서버 연결이 끊겼습니다.", "연결 종료",
                    JOptionPane.WARNING_MESSAGE);
            dispose();
        });
    }

    /** JSON 파싱 (경량 파서: 라이브러리 없이 유연하게 처리) */
    private List<RoomDto> parseRooms(String json) {
        // 가능한 단순한 형식만 처리: [{"name":"x","participants":3,"capacity":10,"active":true,"locked":false}, ...]
        // 실패하면 빈 리스트 반환
        try {
            List<RoomDto> out = new ArrayList<>();
            String arr = json.trim();
            if (!arr.startsWith("[") || !arr.endsWith("]")) return out;
            // 매우 단순 파싱 (쉼표로 객체 분리)
            String body = arr.substring(1, arr.length()-1).trim();
            if (body.isEmpty()) return out;

            // 객체 경계 분리
            int depth = 0; int start = 0;
            for (int i=0;i<body.length();i++) {
                char c = body.charAt(i);
                if (c=='{') depth++;
                else if (c=='}') depth--;
                if (depth==0 && (i==body.length()-1 || body.charAt(i+1)==',')) {
                    String obj = body.substring(start, i+1);
                    out.add(parseRoomObject(obj));
                    start = i+2;
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
        if (s.endsWith("}")) s = s.substring(0, s.length()-1);

        // key:value 쌍 분리 (따옴표, 숫자/불리언만 가정)
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
        try { return Integer.parseInt(v.replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; }
    }
    private boolean parseBool(String v) {
        return v.trim().startsWith("t") || v.trim().startsWith("T");
    }

    private void applyRooms(List<RoomDto> rooms) {
        model.clear();
        for (RoomDto r : rooms) model.addElement(r);

        // 통계 라벨 업데이트
        lblTotalRooms.setText(String.valueOf(rooms.size()));
        int users = rooms.stream().mapToInt(r -> r.participants).sum();
        lblOnlineUsers.setText(String.valueOf(users));
        long active = rooms.stream().filter(r -> r.active).count();
        lblActiveChats.setText(String.valueOf(active));

        // 목록이 클릭으로도 입장 가능
        roomList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) joinSelected();
            }
        });

        // 엔터로 입장
        roomList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "join");
        roomList.getActionMap().put("join", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { joinSelected(); }
        });
    }

    /** 서버 미구현 시 데모용 데이터 */
    private void fillMockRooms() {
        List<RoomDto> demo = List.of(
                new RoomDto("자유채팅방", 3, 10, true, false),
                new RoomDto("프로젝트 회의", 4, 6, true, false),
                new RoomDto("게임 모임", 2, 8, true, false),
                new RoomDto("비밀방", 1, 5, true, true),
                new RoomDto("스터디 그룹", 6, 10, true, false)
        );
        applyRooms(demo);
    }

    /** 목록 셀 렌더러 (이름/카운터/상태/입장 버튼) */
    private class RoomRenderer extends JPanel implements ListCellRenderer<RoomDto> {
        private final JLabel icon = new JLabel("🟦");
        private final JLabel name = new JLabel();
        private final JLabel sub = new JLabel();
        private final JLabel status = new JLabel("● 활성");
        private final JButton joinBtn = new JButton("입장하기");

        public RoomRenderer() {
            setLayout(new BorderLayout(8, 8));
            setBorder(new EmptyBorder(8, 12, 8, 12));

            JPanel left = new JPanel(new BorderLayout());
            left.add(icon, BorderLayout.WEST);

            JPanel text = new JPanel(new GridLayout(2,1));
            text.add(name);
            sub.setForeground(new Color(120,130,140));
            text.add(sub);
            left.add(text, BorderLayout.CENTER);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            status.setForeground(new Color(60, 150, 80));
            right.add(status);
            right.add(joinBtn);

            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);

            joinBtn.addActionListener(e -> {
                // 현재 선택 항목으로 강제 선택 후 입장
                int idx = roomList.getSelectedIndex();
                if (idx >= 0) joinSelected();
            });
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomDto> list, RoomDto value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            name.setText(value.name + (value.locked ? " 🔒" : ""));
            sub.setText(value.toCounter());
            status.setText(value.active ? "● 활성" : "○ 비활성");

            setBackground(isSelected ? new Color(240, 246, 252) : Color.WHITE);
            setOpaque(true);
            return this;
        }
    }
}
