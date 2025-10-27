package chat.client.ui;

import chat.client.ChatClient;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import javax.swing.text.StyledDocument;
import java.util.List;

public class ChatFrame extends JFrame implements ChatClient.MessageListener {

    private final String nickname;
    private final String serverLabel;

    private ChatClient client;

    private JTextPane tpChat;
    private JTextField tfInput;
    private JLabel lblStatus;      // 타이핑/연결상태
    private JButton btnSend;

    // 타이핑 start/stop 전송을 디바운스하기 위한 타이머
    private Timer typingStartTimer;
    private Timer typingStopTimer;
    private volatile boolean typingSent = false;

    public ChatFrame(String nickname, String serverLabel) {
        this.nickname = nickname;
        this.serverLabel = serverLabel;

        setTitle("채팅 - " + nickname + " @" + serverLabel);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 560);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildTop(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        // 타이핑 타이머 (300ms 지연)
        typingStartTimer = new Timer(300, e -> sendTyping(true));
        typingStartTimer.setRepeats(false);

        typingStopTimer  = new Timer(600, e -> sendTyping(false));
        typingStopTimer.setRepeats(false);

        // 창 닫히면 /quit
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (client != null) {
                    try { client.sendMessage("/quit"); client.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private JComponent buildTop() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel l = new JLabel("접속: " + serverLabel + "  |  닉네임: " + nickname);
        l.setForeground(new Color(90, 100, 110));

        lblStatus = new JLabel("연결됨");
        lblStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        lblStatus.setForeground(new Color(90, 100, 110));

        p.add(l, BorderLayout.WEST);
        p.add(lblStatus, BorderLayout.EAST);
        return p;
    }

    private JComponent buildCenter() {
        tpChat = new JTextPane();
        tpChat.setEditable(false);
        // 새 메시지 오면 자동 스크롤
        DefaultCaret caret = (DefaultCaret) tpChat.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JPanel emojis = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        emojis.add(emojiButton("🙂", ":smile:"));
        emojis.add(emojiButton("😂", ":joy:"));
        emojis.add(emojiButton("❤️", ":heart:"));
        emojis.add(emojiButton("👍", ":thumbs:"));
        emojis.add(new JLabel("  (명령 예: /w alice 안녕, /rooms, /join study)"));

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        center.add(new JScrollPane(tpChat), BorderLayout.CENTER);
        center.add(emojis, BorderLayout.SOUTH);
        return center;
    }

    private JButton emojiButton(String face, String token) {
        JButton b = new JButton(face);
        b.setFocusable(false);
        b.addActionListener(e -> {
            tfInput.setText((tfInput.getText() + " " + token).trim());
            tfInput.requestFocus();
        });
        return b;
    }

    private JComponent buildBottom() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        tfInput = new JTextField();
        btnSend = new JButton("전송");

        // 입력 감지 → 타이핑 신호
        tfInput.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { typingChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { typingChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { typingChanged(); }
        });

        // Enter 전송
        tfInput.addActionListener(e -> sendCurrentLine());

        // 버튼 전송
        btnSend.addActionListener(e -> sendCurrentLine());

        p.add(tfInput, BorderLayout.CENTER);
        p.add(btnSend, BorderLayout.EAST);
        return p;
    }

    private void typingChanged() {
        // 사용자가 키보드 입력 → start 예약 / stop 타이머 리셋
        typingStartTimer.restart();
        typingStopTimer.restart();
    }

    private void sendTyping(boolean start) {
        // 서버에서 아직 /typing을 처리하지 않아도 무해함(무시됨).
        if (client == null) return;
        if (start && !typingSent) {
            client.sendMessage("/typing start");
            lblStatus.setText("입력 중…");
            typingSent = true;
        } else if (!start && typingSent) {
            client.sendMessage("/typing stop");
            lblStatus.setText("연결됨");
            typingSent = false;
        }
    }

    private void sendCurrentLine() {
        String line = tfInput.getText().trim();
        if (line.isEmpty() || client == null) return;

        client.sendMessage(line);
        tfInput.setText("");
        typingStopTimer.restart(); // 입력 종료로 간주
    }

    /** ChatClient 바인딩 및 수신 스레드 시작 */
    public void bind(ChatClient client) {
        this.client = client;
        this.client.startReceiving(this);
        tfInput.requestFocus();
    }

    /** 외부(목록 화면)에서 넘어온 버퍼 메시지를 한 번에 출력 */
    public void addBufferedLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            for (String line : lines) appendText(line + "\n");
        });
    }

    /** 수신 메시지 → 채팅창 추가 */
    @Override
    public void onMessageReceived(String line) {
        SwingUtilities.invokeLater(() -> appendText(line + "\n"));
    }

    /** 서버 끊김 */
    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("연결 끊김");
            btnSend.setEnabled(false);
            tfInput.setEnabled(false);
        });
    }


    // ---------- 렌더 유틸 ----------
    private void appendText(String s) {
        try {
            StyledDocument doc = tpChat.getStyledDocument();
            doc.insertString(doc.getLength(), s, null);
        } catch (Exception ignored) {}
    }

    /** 이모티콘/스티커 아이콘을 인라인으로 추가할 때 사용 */
    private void appendIcon(ImageIcon icon) {
        StyledDocument doc = tpChat.getStyledDocument();
        tpChat.setCaretPosition(doc.getLength());
        tpChat.insertIcon(icon);
    }
}
