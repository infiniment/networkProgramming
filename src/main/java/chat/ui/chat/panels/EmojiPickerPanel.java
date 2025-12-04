package chat.ui.chat.panels;

import chat.shared.EmojiRegistry;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class EmojiPickerPanel extends JPanel {
    public interface OnSelect {
        void choose(String code); // 이모티콘 선택 시 호출되는 콜백 함수
    }

    private final OnSelect onSelect;
    private final int iconSize;

    // 생성자: 이모티콘 선택 패널 초기화
    public EmojiPickerPanel(OnSelect onSelect, int iconSize) {
        super(new BorderLayout());
        this.onSelect = onSelect;
        this.iconSize = iconSize;
        build(); // UI 빌드 호출
    }

    // 이모티콘 패널 빌드: 탭 생성 후 각 카테고리별 이모티콘 그리드 생성
    private void build() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP); // 탭을 위쪽에 배치
        // 각 카테고리별 이모티콘 그리드 생성
        for (Map.Entry<String, java.util.List<String>> e : EmojiRegistry.categories().entrySet()) {
            tabs.addTab(e.getKey(), makeGrid(e.getValue())); // 탭에 카테고리 추가
        }
        add(tabs, BorderLayout.CENTER); // 탭을 패널에 추가
    }

    // 이모티콘 코드 목록을 그리드 형식으로 변환하는 함수
    private JScrollPane makeGrid(java.util.List<String> codes) {
        JPanel grid = new JPanel(new GridLayout(0, 6, 8, 8)); // 6열 그리드
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8)); // 그리드 여백 설정
        // 각 이모티콘 코드에 대해 버튼 생성
        for (String code : codes) {
            String path = EmojiRegistry.findEmoji(code);
            if (path == null) continue; // 경로가 없으면 건너뜀
            JButton b = new JButton(scaleIcon(path, iconSize)); // 아이콘 크기 조정
            b.setToolTipText(code); // 툴팁 설정 (이모티콘 코드)
            b.setFocusable(false); // 버튼 포커스 방지
            b.addActionListener(e -> onSelect.choose(code)); // 버튼 클릭 시 이모티콘 선택
            grid.add(b); // 버튼을 그리드에 추가
        }
        return new JScrollPane(grid,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // 그리드를 스크롤 가능하게 설정
    }

    // 이모티콘 아이콘 크기 조정 함수
    private Icon scaleIcon(String path, int size) {
        java.net.URL url = getClass().getClassLoader().getResource(path); // 리소스에서 이모티콘 경로 찾기
        if (url == null) return UIManager.getIcon("OptionPane.informationIcon"); // 경로 없으면 기본 아이콘 반환
        Image img = new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH); // 아이콘 크기 조정
        return new ImageIcon(img); // 크기 조정된 아이콘 반환
    }
}
