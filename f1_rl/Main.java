package f1_rl;

import javax.swing.*;

/**
 * Application entry point.
 * Launches the window on the Swing Event Dispatch Thread.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameWindow::new);
    }
}
