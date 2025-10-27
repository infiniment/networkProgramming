package chat.client.ui;

import javax.swing.*;

public class ClientGuiMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}