package chat.ui.chat.panels;

import chat.shared.EmojiRegistry;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class EmojiPickerPanel extends JPanel {
    public interface OnSelect { void choose(String code); }

    private final OnSelect onSelect;
    private final int iconSize;

    public EmojiPickerPanel(OnSelect onSelect, int iconSize) {
        super(new BorderLayout());
        this.onSelect = onSelect;
        this.iconSize = iconSize;
        build();
    }

    private void build() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        for (Map.Entry<String, java.util.List<String>> e : EmojiRegistry.categories().entrySet()) {
            tabs.addTab(e.getKey(), makeGrid(e.getValue()));
        }
        add(tabs, BorderLayout.CENTER);
    }

    private JScrollPane makeGrid(java.util.List<String> codes) {
        JPanel grid = new JPanel(new GridLayout(0, 6, 8, 8)); // 6열 그리드
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (String code : codes) {
            String path = EmojiRegistry.findEmoji(code);
            if (path == null) continue;
            JButton b = new JButton(scaleIcon(path, iconSize));
            b.setToolTipText(code);
            b.setFocusable(false);
            b.addActionListener(e -> onSelect.choose(code));
            grid.add(b);
        }
        return new JScrollPane(grid,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    private Icon scaleIcon(String path, int size) {
        java.net.URL url = getClass().getClassLoader().getResource(path);
        if (url == null) return UIManager.getIcon("OptionPane.informationIcon");
        Image img = new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }
}
