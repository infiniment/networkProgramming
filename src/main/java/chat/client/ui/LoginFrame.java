package chat.client.ui;

import chat.client.ChatClient;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class LoginFrame extends JFrame {
    private JTextField tfNickname;
    private JTextField tfHost;
    private JTextField tfPort;
    private JButton btnStart;

    public LoginFrame() {
        setTitle("멀티룸 채팅 - 시작화면");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(460, 560);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 0, 24));

        JLabel title = new JLabel("멀티룸 채팅", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setBorder(BorderFactory.createEmptyBorder(16, 0, 8, 0));

        JLabel sub = new JLabel("네트워크 프로그래밍 프로젝트", SwingConstants.CENTER);
        sub.setFont(sub.getFont().deriveFont(Font.BOLD, 14f));
        sub.setForeground(new Color(90, 100, 110));

        panel.add(title, BorderLayout.NORTH);
        panel.add(sub, BorderLayout.CENTER);

        return panel;
    }

    private JComponent buildForm() {
        JPanel card = new JPanel();
        card.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        tfNickname = new JTextField();
        tfHost = new JTextField("localhost");
        tfPort = new JTextField("5959");

        card.add(label("닉네임"));
        card.add(field(tfNickname));
        card.add(Box.createVerticalStrut(16));

        card.add(label("서버 IP"));
        card.add(field(tfHost));
        card.add(Box.createVerticalStrut(16));

        card.add(label("포트"));
        card.add(field(tfPort));
        card.add(Box.createVerticalStrut(24));

        btnStart = new JButton("채팅 시작하기");
        btnStart.setEnabled(false);
        btnStart.setPreferredSize(new Dimension(0, 44));
        btnStart.addActionListener(e -> connectAndOpenChat());
        card.add(btnStart);

        // 실시간 검증
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { validateForm(); }
            public void removeUpdate(DocumentEvent e) { validateForm(); }
            public void changedUpdate(DocumentEvent e) { validateForm(); }
        };

        tfNickname.getDocument().addDocumentListener(dl);
        tfHost.getDocument().addDocumentListener(dl);
        tfPort.getDocument().addDocumentListener(dl);

        return card;
    }

    private JComponent buildFooter() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 16, 0));
        JLabel caps = new JLabel("• 미니게임 지원    • 이모티콘    • 비밀모드");
        caps.setForeground(new Color(120, 130, 140));
        panel.add(caps);
        return panel;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return l;
    }

    private JComponent field(JTextField tf) {
        tf.setPreferredSize(new Dimension(0, 40));
        return wrap(tf);
    }

    private JComponent wrap(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void validateForm() {
        String nick = tfNickname.getText().trim();
        String host = tfHost.getText().trim();
        String port = tfPort.getText().trim();
        boolean ok = !nick.isBlank() && !host.isBlank();
        try { ok = ok && Integer.parseInt(port) > 0; } catch (Exception ignored) { ok = false; }
        btnStart.setEnabled(ok);
    }

    private void connectAndOpenChat() {
        String nick = tfNickname.getText().trim();
        String host = tfHost.getText().trim();
        int port = Integer.parseInt(tfPort.getText().trim());

        btnStart.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            ChatClient client;
            Exception error;

            @Override
            protected Void doInBackground() {
                try {
                    client = new ChatClient();
                    client.connect(host, port);
                    // 서버가 첫 줄을 닉네임으로 처리하므로 바로 전송
                    client.sendMessage(nick);
                } catch (Exception ex) { error = ex; }
                return null;
            }

            @Override
            protected void done() {
                btnStart.setEnabled(true);
                if (error != null) {
                    JOptionPane.showMessageDialog(LoginFrame.this,
                            "서버 연결 실패:\n" + error.getMessage(),
                            "오류", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // 채팅방 목록으로 변경
                RoomListFrame rooms = new RoomListFrame(nick, host + ":" + port);
                rooms.bind(client);     // ChatClient 바인딩
                rooms.setVisible(true);
                // 로그인 창 닫기
                dispose();
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 배경 톤만 맞춰서 이미지 느낌 살짝
            JFrame f = new LoginFrame();
            f.getContentPane().setBackground(new Color(200, 225, 222));
            f.setVisible(true);
            // 안전 종료
            f.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { System.exit(0); }
            });
        });
    }


}
