package chat.ui.main;

import javax.swing.*;

public class ClientGuiMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}