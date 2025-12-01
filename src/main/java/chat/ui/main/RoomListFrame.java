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
import java.util.concurrent.ConcurrentHashMap;

/**
 * RoomListFrame - 채팅방 목록 화면
 */
public class RoomListFrame extends JFrame implements ChatClient.MessageListener {

    // HashMap은 수신스레드/EDT 동시 접근 위험 -> ConcurrentHashMap 추천
    private final Map<String, ChatFrame> openChatFrames = new ConcurrentHashMap<>();

    // 현재 포커스/활성 채팅창 추적
    private volatile ChatFrame activeChatFrame = null;

    private Component getPopupParent() {
        ChatFrame a = activeChatFrame;
        if (a != null && a.isShowing()) return a;

        // 활성창이 없으면 아무 채팅창이라도 하나
        for (ChatFrame f : openChatFrames.values()) {
            if (f != null && f.isShowing()) return f;
        }
        return this; // 채팅창이 없으면 목록이 부모
    }

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

    private final DefaultListModel<RoomDto> model = new DefaultListModel<>();
    private JList<RoomDto> roomList;
    private JButton btnCreate;
    private JButton btnRefresh;

    private JLabel lblStatusIcon;
    private JLabel lblStatusText;

    private final List<String> passthroughLog = new CopyOnWriteArrayList<>();

    // 🔧 게임 메시지 버퍼
    private final List<String> gameMessageBuffer = new CopyOnWriteArrayList<>();

    // 🔑 비밀방 입장 대기 상태
    private String pendingRoomJoin = null;
    private String pendingRoomPassword = null;

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
        JPanel header = new RoundedPanel(18);
        header.setBackground(CARD_BG);
        header.setBorder(new EmptyBorder(18, 24, 18, 24));
        header.setLayout(new BorderLayout(20, 0));
        header.setPreferredSize(new Dimension(0, 80));

        // -------- 왼쪽: 타이틀 + 서브타이틀 --------
        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("오픈 채팅방");
        title.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("관심 있는 주제의 채팅방에 바로 참여해 보세요");
        subtitle.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
        subtitle.setForeground(new Color(120, 130, 140));

        leftPanel.add(title);
        leftPanel.add(Box.createVerticalStrut(4));
        leftPanel.add(subtitle);

        // -------- 오른쪽: 상태 + 유저 정보 --------
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        lblStatusIcon = new JLabel(makeStatusIcon(PRIMARY));
        lblStatusText = new JLabel("연결됨");
        lblStatusText.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 12));
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

        rightPanel.add(right, BorderLayout.CENTER);

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

        // -------- 상단 타이틀 영역 --------
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel sectionTitle = new JLabel("현재 열려 있는 채팅방");
        sectionTitle.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        sectionTitle.setForeground(TEXT_PRIMARY);

        JLabel sectionSub = new JLabel("새로고침하면 최신 참여자 수와 활성 상태가 반영됩니다");
        sectionSub.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        sectionSub.setForeground(new Color(140, 148, 160));

        titleBox.add(sectionTitle);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(sectionSub);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        btnRefresh = createActionButton("새로고침", false);
        btnRefresh.addActionListener(e -> requestRooms());

        btnCreate = createActionButton("+ 방 만들기", true);
        btnCreate.addActionListener(e -> showCreateDialog());

        actions.add(btnRefresh);
        actions.add(btnCreate);

        top.add(titleBox, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        // -------- 리스트 영역 --------
        roomList = new JList<>(model);
        roomList.setCellRenderer(new RoomRenderer());
        roomList.setBackground(new Color(250, 250, 250));
        roomList.setSelectionBackground(new Color(255, 244, 233));
        roomList.setSelectionForeground(TEXT_PRIMARY);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFixedCellHeight(80);

        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 더블클릭 입장 완전 차단
                if (e.getClickCount() >= 2) {
                    e.consume();
                    return;
                }

                int index = roomList.locationToIndex(e.getPoint());
                if (index < 0) return;

                Rectangle cell = roomList.getCellBounds(index, index);
                if (cell == null || !cell.contains(e.getPoint())) return;

                int relX = e.getX() - cell.x;
                int w = cell.width;

                // Renderer 기준(너가 설정한 버튼 크기랑 border 기준)
                final int RIGHT_PAD = 16;   // RoomRenderer border right
                final int GAP = 8;          // FlowLayout hgap
                final int JOIN_W = 72;
                final int DELETE_W = 64;

                int deleteStartX = w - RIGHT_PAD - DELETE_W;              // 삭제 버튼 시작
                int joinStartX   = deleteStartX - GAP - JOIN_W;           // 입장 버튼 시작

                // 삭제 버튼 영역
                if (relX >= deleteStartX) {
                    e.consume();
                    RoomDto r = model.get(index);

                    int res = JOptionPane.showConfirmDialog(
                            getPopupParent(),
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

                // 입장 버튼 영역
                if (relX >= joinStartX) {
                    e.consume();
                    roomList.setSelectedIndex(index);
                    joinSelected();
                    return;
                }

                // 나머지 영역은 선택만
                roomList.setSelectedIndex(index);
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

                final int RIGHT_PAD = 16;
                final int GAP = 8;
                final int JOIN_W = 72;
                final int DELETE_W = 64;

                int deleteStartX = w - RIGHT_PAD - DELETE_W;
                int joinStartX   = deleteStartX - GAP - JOIN_W;

                if (relX >= joinStartX) roomList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                else roomList.setCursor(Cursor.getDefaultCursor());
            }
        });

        JScrollPane scroll = new JScrollPane(roomList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(250, 250, 250));

        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ========== 상단 버튼 ==========
    private JButton createActionButton(String text, boolean isPrimary) {
        JButton btn = new JButton() {
            private boolean hover = false;
            private final String buttonText = text;

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

                if (isPrimary) g2.setColor(hover ? PRIMARY_HOVER : PRIMARY);
                else g2.setColor(hover ? ACCENT_LIGHT : new Color(247, 249, 252));

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
    void showCreateDialog() {
        JTextField tfName = new JTextField();
        tfName.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));

        JSpinner spCap = new JSpinner(new SpinnerNumberModel(10, 2, 99, 1));
        spCap.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));

        JCheckBox ckLock = new JCheckBox("비밀방 (잠금)");
        ckLock.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));

        JLabel lblPassword = new JLabel("비밀번호 (4자리 숫자)");
        lblPassword.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 13));
        lblPassword.setVisible(false);

        JTextField tfPassword = new JTextField();
        tfPassword.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
        tfPassword.setVisible(false);

        tfPassword.setDocument(new javax.swing.text.PlainDocument() {
            @Override
            public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str == null) return;
                if ((getLength() + str.length() <= 4) && str.matches("[0-9]+")) {
                    super.insertString(offs, str, a);
                }
            }
        });

        ckLock.addActionListener(e -> {
            boolean checked = ckLock.isSelected();
            lblPassword.setVisible(checked);
            tfPassword.setVisible(checked);
            tfPassword.setText("");

            java.awt.Window win = SwingUtilities.getWindowAncestor(ckLock);
            if (win != null) {
                win.pack();
                win.setLocationRelativeTo(RoomListFrame.this);
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_COLOR);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        RoundedPanel card = new RoundedPanel(18);
        card.setBackground(Color.WHITE);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("새 오픈 채팅방 만들기");
        title.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 16));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("방 이름과 정원, 필요하다면 비밀방 비밀번호를 설정하세요.");
        subtitle.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        subtitle.setForeground(new Color(120, 130, 140));

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel lblName = new JLabel("방 이름");
        lblName.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 13));

        tfName.setBackground(new Color(250, 250, 252));
        tfName.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 228, 234), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        form.add(lblName);
        form.add(Box.createVerticalStrut(4));
        form.add(tfName);
        form.add(Box.createVerticalStrut(12));

        JLabel lblCap = new JLabel("정원");
        lblCap.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 13));

        JComponent capEditor = spCap.getEditor();
        if (capEditor instanceof JSpinner.DefaultEditor) {
            JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) capEditor;
            editor.getTextField().setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));
            editor.getTextField().setBackground(new Color(250, 250, 252));
            editor.getTextField().setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(225, 228, 234), 1, true),
                    new EmptyBorder(8, 10, 8, 10)
            ));
        }

        JPanel capRow = new JPanel(new BorderLayout(8, 0));
        capRow.setOpaque(false);
        capRow.add(spCap, BorderLayout.WEST);

        JLabel capHint = new JLabel("명 (2 ~ 99명)");
        capHint.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        capHint.setForeground(new Color(140, 148, 160));
        capRow.add(capHint, BorderLayout.CENTER);

        form.add(lblCap);
        form.add(Box.createVerticalStrut(4));
        form.add(capRow);
        form.add(Box.createVerticalStrut(12));

        ckLock.setOpaque(false);
        form.add(ckLock);
        form.add(Box.createVerticalStrut(6));

        JPanel pwRow = new JPanel(new BorderLayout(8, 0));
        pwRow.setOpaque(false);

        tfPassword.setBackground(new Color(250, 250, 252));
        tfPassword.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 228, 234), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        pwRow.add(lblPassword, BorderLayout.WEST);
        pwRow.add(tfPassword, BorderLayout.CENTER);
        form.add(pwRow);

        JLabel hint = new JLabel("• 비밀방을 선택하면 4자리 숫자 비밀번호가 필요해요.");
        hint.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
        hint.setForeground(new Color(160, 168, 178));

        card.add(header, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);

        JPanel hintWrapper = new JPanel(new BorderLayout());
        hintWrapper.setOpaque(false);
        hintWrapper.setBorder(new EmptyBorder(4, 0, 0, 0));
        hintWrapper.add(hint, BorderLayout.CENTER);
        card.add(hintWrapper, BorderLayout.SOUTH);

        root.add(card, BorderLayout.CENTER);

        int ok = JOptionPane.showConfirmDialog(
                this,
                root,
                "새 방 만들기",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (ok == JOptionPane.OK_OPTION) {
            String name = tfName.getText().trim();
            String nameArg = name.contains(" ") ? "\"" + name + "\"" : name;
            int cap = (Integer) spCap.getValue();
            boolean lock = ckLock.isSelected();
            String password = tfPassword.getText().trim();

            if (lock && password.length() != 4) {
                JOptionPane.showMessageDialog(
                        getPopupParent(), // ✅ 채팅창이 열려있으면 채팅창 위로
                        "비밀번호는 4자리 숫자여야 합니다.",
                        "입력 오류",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            if (!name.isEmpty() && client != null) {
                String lockStatus = lock ? "lock" : "open";
                String cmd = lock
                        ? String.format("%s %s %d %s %s", Constants.CMD_ROOM_CREATE, nameArg, cap, lockStatus, password)
                        : String.format("%s %s %d %s", Constants.CMD_ROOM_CREATE, nameArg, cap, lockStatus);

                client.sendMessage(cmd);
                requestRooms();
            }
        }
    }

    // ========== 방 입장 ==========
    private void joinSelected() {
        RoomDto r = roomList.getSelectedValue();
        if (r == null || client == null) return;

        // 이미 열린 방이면 앞으로 (+ active 갱신)
        if (openChatFrames.containsKey(r.name)) {
            ChatFrame existingChat = openChatFrames.get(r.name);
            activeChatFrame = existingChat;
            existingChat.toFront();
            existingChat.requestFocus();
            return;
        }

        String roomArg = (r.name != null && r.name.contains(" "))
                ? "\"" + r.name + "\""
                : r.name;

        if (r.locked) {
            String inputPassword = showPasswordDialog();
            if (inputPassword == null) return;

            if (inputPassword.length() != 4) {
                JOptionPane.showMessageDialog(
                        getPopupParent(),
                        "비밀번호는 4자리 숫자입니다.",
                        "입력 오류",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            client.sendMessage(Constants.CMD_JOIN_ROOM + " " + roomArg + " " + inputPassword);

            pendingRoomJoin = r.name;
            pendingRoomPassword = inputPassword;
        } else {
            client.sendMessage(Constants.CMD_JOIN_ROOM + " " + roomArg);
            openChatFrameForRoom(r);
        }
    }

    // 방 입장 성공 시 ChatFrame 열기
    private void openChatFrameForRoom(RoomDto r) {
        if (openChatFrames.containsKey(r.name)) {
            ChatFrame existingChat = openChatFrames.get(r.name);
            activeChatFrame = existingChat;
            existingChat.toFront();
            existingChat.requestFocus();
            return;
        }

        ChatFrame chat = new ChatFrame(nickname, serverLabel + " · " + r.name, this);
        openChatFrames.put(r.name, chat);

        // ✅ 활성 채팅창 추적
        activeChatFrame = chat;
        chat.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                activeChatFrame = chat;
            }
        });

        chat.updateMemberCount(r.participants);
        chat.bind(client);

        for (String line : passthroughLog) chat.onMessageReceived(line);
        passthroughLog.clear();

        for (String gameLine : gameMessageBuffer) chat.onMessageReceived(gameLine);
        gameMessageBuffer.clear();

        chat.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                openChatFrames.remove(r.name);
                if (activeChatFrame == chat) activeChatFrame = null;
            }
        });

        chat.setVisible(true);
    }

    // 🔑 비밀번호 입력 다이얼로그
    private String showPasswordDialog() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel lblInfo = new JLabel("이 방은 비밀방입니다. 비밀번호를 입력하세요.");
        lblInfo.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 13));

        JTextField tfPassword = new JTextField();
        tfPassword.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 14));

        tfPassword.setDocument(new javax.swing.text.PlainDocument() {
            @Override
            public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str == null) return;
                if ((getLength() + str.length() <= 4) && str.matches("[0-9]+")) {
                    super.insertString(offs, str, a);
                }
            }
        });

        panel.add(lblInfo);
        panel.add(tfPassword);

        int result = JOptionPane.showConfirmDialog(
                getPopupParent(), // ✅ 채팅창이 열려있으면 채팅창 위로
                panel,
                "🔒 비밀방 입장",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) return tfPassword.getText().trim();
        return null;
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

            // 🔒 비밀번호 오류 처리
            if (message.contains("비밀번호가 틀렸습니다") || message.contains("비밀번호 불일치")) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        getPopupParent(), // ✅ 부모를 채팅창으로
                        "비밀번호가 틀렸습니다.",
                        "입장 실패",
                        JOptionPane.ERROR_MESSAGE
                ));
                pendingRoomJoin = null;
                pendingRoomPassword = null;
                return;
            }

            // ✅ 방 입장 성공 메시지 처리
            if (message.contains("입장하였습니다") && pendingRoomJoin != null) {
                String roomName = pendingRoomJoin;

                RoomDto targetRoom = null;
                for (int i = 0; i < model.getSize(); i++) {
                    RoomDto r = model.getElementAt(i);
                    if (r.name.equals(roomName)) {
                        targetRoom = r;
                        break;
                    }
                }

                if (targetRoom != null) {
                    RoomDto finalRoom = targetRoom;
                    SwingUtilities.invokeLater(() -> openChatFrameForRoom(finalRoom));
                }

                pendingRoomJoin = null;
                pendingRoomPassword = null;

                for (ChatFrame frame : openChatFrames.values()) frame.onMessageReceived(line);
                return;
            }

            // 🔔 기타 시스템 메시지: 팝업 parent를 "활성 ChatFrame"으로 (목록이 앞으로 안 튐)
            SwingUtilities.invokeLater(() -> {
                int type = (message.contains("실패") || message.contains("권한") || message.contains("없습니다"))
                        ? JOptionPane.WARNING_MESSAGE
                        : JOptionPane.INFORMATION_MESSAGE;

                JOptionPane.showMessageDialog(
                        getPopupParent(), // ✅ RoomListFrame.this -> getPopupParent()
                        message,
                        "시스템 알림",
                        type
                );
            });

            for (ChatFrame frame : openChatFrames.values()) frame.onMessageReceived(line);
            return;
        }

        // 구규격 게임 메시지
        if (line.startsWith("[GAME]")) {
            for (ChatFrame frame : openChatFrames.values()) frame.onMessageReceived(line);
            if (openChatFrames.isEmpty()) gameMessageBuffer.add(line);
            return;
        }

        // 일반 채팅
        for (ChatFrame frame : openChatFrames.values()) frame.onMessageReceived(line);
        if (openChatFrames.isEmpty()) passthroughLog.add(line);
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            if (lblStatusIcon != null) lblStatusIcon.setIcon(makeStatusIcon(Color.RED));
            if (lblStatusText != null) lblStatusText.setText("연결 끊김");
            JOptionPane.showMessageDialog(getPopupParent(), "서버 연결이 끊겼습니다.", "연결 종료",
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
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
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
        } catch (Exception ignored) {}
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
        private final JLabel badgeLock = new JLabel();
        private final JLabel badgeCount = new JLabel();
        private final JButton joinBtn;
        private final JButton deleteBtn;

        private boolean selected;
        private boolean locked;

        public RoomRenderer() {
            setLayout(new BorderLayout(16, 0));
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setOpaque(false);

            JPanel leftCol = new JPanel();
            leftCol.setOpaque(false);
            leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));

            JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            iconRow.setOpaque(false);
            icon.setFont(new Font("Dialog", Font.PLAIN, 22));
            icon.setPreferredSize(new Dimension(30, 30));
            iconRow.add(icon);

            JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            badgeRow.setOpaque(false);

            badgeLock.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            badgeLock.setBorder(new EmptyBorder(2, 6, 2, 6));

            badgeCount.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 10));
            badgeCount.setBorder(new EmptyBorder(2, 6, 2, 6));

            badgeRow.add(badgeLock);
            badgeRow.add(badgeCount);

            leftCol.add(iconRow);
            leftCol.add(Box.createVerticalStrut(6));
            leftCol.add(badgeRow);

            JPanel centerCol = new JPanel();
            centerCol.setOpaque(false);
            centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));

            name.setFont(loadCustomFont("BMDOHYEON_ttf.ttf", Font.BOLD, 15));
            name.setForeground(TEXT_PRIMARY);
            name.setHorizontalAlignment(SwingConstants.CENTER);
            name.setAlignmentX(0.5f);

            sub.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
            sub.setForeground(new Color(130, 138, 150));
            sub.setHorizontalAlignment(SwingConstants.CENTER);
            sub.setAlignmentX(0.5f);

            centerCol.add(name);
            centerCol.add(Box.createVerticalStrut(4));
            centerCol.add(sub);

            JPanel rightCol = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightCol.setOpaque(false);

            status.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.PLAIN, 11));
            status.setPreferredSize(new Dimension(56, 20));

            joinBtn = createSmallButton("입장");
            deleteBtn = createDeleteButton("삭제");

            rightCol.add(status);
            rightCol.add(joinBtn);
            rightCol.add(deleteBtn);

            JPanel leftWrapper = new JPanel(new GridBagLayout());
            leftWrapper.setOpaque(false);
            GridBagConstraints gbcLeft = new GridBagConstraints();
            gbcLeft.gridx = 0; gbcLeft.gridy = 0;
            gbcLeft.anchor = GridBagConstraints.WEST;
            leftWrapper.add(leftCol, gbcLeft);

            JPanel centerWrapper = new JPanel(new GridBagLayout());
            centerWrapper.setOpaque(false);
            GridBagConstraints gbcCenter = new GridBagConstraints();
            gbcCenter.gridx = 0; gbcCenter.gridy = 0;
            gbcCenter.anchor = GridBagConstraints.CENTER;
            centerWrapper.add(centerCol, gbcCenter);

            JPanel rightWrapper = new JPanel(new GridBagLayout());
            rightWrapper.setOpaque(false);
            GridBagConstraints gbcRight = new GridBagConstraints();
            gbcRight.gridx = 0; gbcRight.gridy = 0;
            gbcRight.anchor = GridBagConstraints.EAST;
            rightWrapper.add(rightCol, gbcRight);

            add(leftWrapper, BorderLayout.WEST);
            add(centerWrapper, BorderLayout.CENTER);
            add(rightWrapper, BorderLayout.EAST);
        }

        private JButton createSmallButton(String text) {
            JButton btn = new JButton(text) {
                private boolean btnHover = false;
                private boolean btnPressed = false;

                {
                    setFocusPainted(false);
                    setBorderPainted(false);
                    setContentAreaFilled(false);
                    setOpaque(false);
                    setCursor(new Cursor(Cursor.HAND_CURSOR));

                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { btnHover = true; repaint(); } }
                        @Override public void mouseExited (MouseEvent e) { btnHover = false; btnPressed = false; repaint(); }
                        @Override public void mousePressed (MouseEvent e) { if (isEnabled()) { btnPressed = true; repaint(); } }
                        @Override public void mouseReleased(MouseEvent e) { btnPressed = false; repaint(); }
                        @Override public void mouseClicked (MouseEvent e) { e.consume(); }
                    });
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    Color base = PRIMARY;
                    if (!isEnabled()) base = new Color(200, 200, 200);
                    else if (btnPressed) base = new Color(255, 120, 20);
                    else if (btnHover) base = PRIMARY_HOVER;

                    int offsetY = btnPressed ? 1 : 0;
                    g2.translate(0, offsetY);
                    g2.setColor(base);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight() - offsetY, 10, 10);

                    g2.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
                    g2.setColor(Color.WHITE);
                    FontMetrics fm = g2.getFontMetrics();
                    int w = fm.stringWidth(text);
                    int h = fm.getAscent();
                    int x = (getWidth() - w) / 2;
                    int y = (getHeight() + h) / 2 - 2;
                    g2.drawString(text, x, y);

                    g2.dispose();
                }
            };
            btn.setPreferredSize(new Dimension(72, 30));
            return btn;
        }

        private JButton createDeleteButton(String text) {
            JButton btn = new JButton(text) {
                private boolean hover = false;
                private boolean pressed = false;

                {
                    setFocusPainted(false);
                    setBorderPainted(false);
                    setContentAreaFilled(false);
                    setOpaque(false);
                    setCursor(new Cursor(Cursor.HAND_CURSOR));
                    setMargin(new Insets(0, 0, 0, 0));

                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                        @Override public void mouseExited (MouseEvent e) { hover = false; pressed = false; repaint(); }
                        @Override public void mousePressed (MouseEvent e) { pressed = true; repaint(); }
                        @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
                        @Override public void mouseClicked (MouseEvent e) { e.consume(); }
                    });
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    Color base;
                    if (pressed) base = new Color(220, 38, 38);
                    else if (hover) base = new Color(248, 113, 113);
                    else base = new Color(239, 68, 68);

                    g2.setColor(base);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                    g2.setFont(loadCustomFont("BMHANNAAir_ttf.ttf", Font.BOLD, 12));
                    g2.setColor(Color.WHITE);
                    FontMetrics fm = g2.getFontMetrics();
                    int w = fm.stringWidth(text);
                    int h = fm.getAscent();
                    int x = (getWidth() - w) / 2;
                    int y = (getHeight() + h) / 2 - 2;
                    g2.drawString(text, x, y);

                    g2.dispose();
                }
            };

            btn.setPreferredSize(new Dimension(64, 30));
            return btn;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color cardBg;
            if (selected) cardBg = new Color(255, 244, 233);
            else if (locked) cardBg = new Color(255, 247, 239);
            else cardBg = Color.WHITE;

            int arc = 16;
            g2.setColor(cardBg);
            g2.fillRoundRect(4, 4, getWidth() - 8, getHeight() - 8, arc, arc);

            g2.setColor(new Color(0, 0, 0, 10));
            g2.drawRoundRect(4, 4, getWidth() - 8, getHeight() - 8, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomDto> list, RoomDto value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            this.selected = isSelected;
            this.locked = value.locked;

            icon.setText(value.locked ? "🔒" : "💬");
            name.setText(value.name);
            sub.setText(value.toCounter());

            status.setText(value.active ? "LIVE" : "대기");
            status.setForeground(value.active ? PRIMARY : new Color(160, 160, 160));

            if (value.locked) {
                badgeLock.setText("비밀방");
                badgeLock.setForeground(new Color(190, 120, 80));
                badgeLock.setOpaque(true);
                badgeLock.setBackground(new Color(255, 241, 231));
            } else {
                badgeLock.setText("공개방");
                badgeLock.setForeground(new Color(88, 101, 242));
                badgeLock.setOpaque(true);
                badgeLock.setBackground(new Color(235, 240, 255));
            }

            badgeCount.setText(value.participants + " / " + value.capacity + "명");
            badgeCount.setForeground(new Color(90, 98, 110));
            badgeCount.setOpaque(true);
            badgeCount.setBackground(new Color(245, 247, 250));

            for (ActionListener al : joinBtn.getActionListeners()) joinBtn.removeActionListener(al);
            joinBtn.addActionListener(e -> {
                roomList.setSelectedIndex(index);
                joinSelected();
            });

            for (ActionListener al : deleteBtn.getActionListeners()) deleteBtn.removeActionListener(al);
            deleteBtn.setToolTipText("방 삭제");
            deleteBtn.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(
                        getPopupParent(), // 채팅창이 열려있으면 채팅창 위로
                        "'" + value.name + "' 방을 삭제하시겠습니까?",
                        "방 삭제",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (res == JOptionPane.OK_OPTION && client != null) {
                    client.sendMessage(Constants.CMD_ROOM_DELETE + " " + value.name);
                }
            });

            return this;
        }
    }
}
