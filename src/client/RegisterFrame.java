package client;

import util.Constants;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * RegisterFrame.java  — redesigned
 * ─────────────────────────────────
 * Same split-panel layout as LoginFrame (brand left / form right).
 * Validates all fields client-side before sending to server.
 */
public class RegisterFrame extends JFrame {

    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JButton        registerButton;
    private JButton        backButton;
    private JLabel         statusLabel;

    private final LoginFrame loginFrame;
    private ChatClient tempClient;

    // ── Colours ─────────────────────────────────────────────────────
    private static final Color BG_PANEL   = new Color(28, 28, 42);
    private static final Color ACCENT     = new Color(88, 101, 242);
    private static final Color TEXT       = new Color(220, 220, 235);
    private static final Color TEXT_DIM   = new Color(130, 130, 160);
    private static final Color ERROR_COL  = new Color(240,  71,  71);
    private static final Color SUCCESS_COL= new Color( 67, 181, 129);

    // ────────────────────────────────────────────────────────────────
    public RegisterFrame(LoginFrame loginFrame) {
        this.loginFrame = loginFrame;
        setTitle(Constants.APP_TITLE + "  ·  Create Account");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(860, 580);
        setMinimumSize(new Dimension(720, 500));
        setLocationRelativeTo(loginFrame);
        buildUI();
    }

    // ════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        JPanel root = new JPanel(new GridLayout(1, 2));
        root.setBackground(new Color(18, 18, 28));
        setContentPane(root);
        root.add(buildBrandPanel());
        root.add(buildFormPanel());
    }

    private JPanel buildBrandPanel() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(40, 60, 170),
                        getWidth(), getHeight(), new Color(15, 15, 45));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 15));
                g2.fillOval(-80, getHeight() - 200, 320, 320);
                g2.fillOval(getWidth() - 100, -60, 240, 240);
            }
        };
        panel.setLayout(new GridBagLayout());
        panel.setOpaque(false);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel("✨");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel name = LoginFrame.centeredLabel("Join SecureChat", 28, Font.BOLD, Color.WHITE);
        JLabel sub  = LoginFrame.centeredLabel("Create your free account", 13, Font.PLAIN, new Color(190, 200, 255));

        inner.add(icon);
        inner.add(Box.createVerticalStrut(14));
        inner.add(name);
        inner.add(Box.createVerticalStrut(8));
        inner.add(sub);
        inner.add(Box.createVerticalStrut(32));
        inner.add(stepRow("1", "Choose a unique username"));
        inner.add(Box.createVerticalStrut(10));
        inner.add(stepRow("2", "Create a strong password"));
        inner.add(Box.createVerticalStrut(10));
        inner.add(stepRow("3", "Start chatting securely"));

        panel.add(inner);
        return panel;
    }

    private JPanel stepRow(String num, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);

        JLabel numLbl = new JLabel(num);
        numLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        numLbl.setForeground(Color.WHITE);
        numLbl.setPreferredSize(new Dimension(26, 26));
        numLbl.setHorizontalAlignment(SwingConstants.CENTER);
        numLbl.setOpaque(true);
        numLbl.setBackground(new Color(88, 101, 242, 180));
        numLbl.setBorder(new EmptyBorder(3, 0, 3, 0));

        JLabel txtLbl = new JLabel(text);
        txtLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtLbl.setForeground(new Color(210, 215, 255));

        row.add(numLbl);
        row.add(txtLbl);
        return row;
    }

    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG_PANEL);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(340, 480));

        JLabel title    = LoginFrame.centeredLabel("Create Account", 22, Font.BOLD, TEXT);
        JLabel subtitle = LoginFrame.centeredLabel("All fields are required", 12, Font.PLAIN, TEXT_DIM);

        // Fields
        usernameField = LoginFrame.styledField("3–20 chars, letters/digits/_");
        passwordField = LoginFrame.styledPasswordField("Minimum 6 characters");
        confirmField  = LoginFrame.styledPasswordField("Re-enter your password");

        // Password strength bar
        JPanel strengthBar = buildStrengthBar();

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(ERROR_COL);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        registerButton = LoginFrame.accentButton("Create Account", ACCENT);
        backButton     = LoginFrame.ghostButton("← Back to Login");

        // Wire events
        registerButton.addActionListener(e -> attemptRegister());
        backButton.addActionListener(e -> {
            dispose();
            loginFrame.setVisible(true);
        });
        confirmField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) attemptRegister();
            }
        });

        // Update strength bar as user types
        passwordField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                updateStrength(new String(passwordField.getPassword()), strengthBar);
            }
        });

        // Assemble
        form.add(title);
        form.add(Box.createVerticalStrut(4));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(22));
        form.add(LoginFrame.fieldLabel("Username"));
        form.add(Box.createVerticalStrut(6));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(14));
        form.add(LoginFrame.fieldLabel("Password"));
        form.add(Box.createVerticalStrut(6));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(6));
        form.add(strengthBar);
        form.add(Box.createVerticalStrut(14));
        form.add(LoginFrame.fieldLabel("Confirm Password"));
        form.add(Box.createVerticalStrut(6));
        form.add(confirmField);
        form.add(Box.createVerticalStrut(10));
        form.add(statusLabel);
        form.add(Box.createVerticalStrut(14));
        form.add(registerButton);
        form.add(Box.createVerticalStrut(10));
        form.add(backButton);

        outer.add(form);
        return outer;
    }

    // ── Password strength bar ─────────────────────────────────────

    private JPanel buildStrengthBar() {
        JPanel bar = new JPanel(new GridLayout(1, 4, 4, 0));
        bar.setOpaque(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < 4; i++) {
            JPanel seg = new JPanel();
            seg.setBackground(new Color(60, 60, 80));
            seg.setOpaque(true);
            bar.add(seg);
        }
        return bar;
    }

    private void updateStrength(String pw, JPanel bar) {
        int score = 0;
        if (pw.length() >= 6)  score++;
        if (pw.length() >= 10) score++;
        if (pw.matches(".*[A-Z].*") && pw.matches(".*[0-9].*")) score++;
        if (pw.matches(".*[^a-zA-Z0-9].*")) score++;
        Color[] colors = {
            new Color(240, 71, 71),
            new Color(250, 180, 50),
            new Color(88, 200, 100),
            new Color(67, 181, 129)
        };
        for (int i = 0; i < 4; i++) {
            ((JPanel) bar.getComponent(i)).setBackground(
                i < score ? colors[score - 1] : new Color(60, 60, 80));
        }
        bar.repaint();
    }

    // ════════════════════════════════════════════════════════════════
    //  REGISTRATION LOGIC
    // ════════════════════════════════════════════════════════════════

    private void attemptRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm  = new String(confirmField.getPassword());

        if (username.isEmpty())                        { showError("Username cannot be empty."); return; }
        if (!username.matches("[a-zA-Z0-9_]{3,20}"))   { showError("Username: 3–20 chars, letters/digits/underscore only."); return; }
        if (password.isEmpty())                        { showError("Password cannot be empty."); return; }
        if (password.length() < 6)                     { showError("Password must be at least 6 characters."); return; }
        if (!password.equals(confirm))                 {
            showError("Passwords do not match.");
            passwordField.setText("");
            confirmField.setText("");
            return;
        }

        setFormEnabled(false);
        showStatus("Connecting…", TEXT_DIM);

        final String u = username, p = password;
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                tempClient = new ChatClient();
                // Inline ServerCallback — routes responses to this RegisterFrame
                ServerCallback cb = new ServerCallback() {
                    @Override public void onLoginSuccess(String username) { /* no-op */ }
                    @Override public void showError(String msg) {
                        SwingUtilities.invokeLater(() -> onRegisterFailure(msg));
                    }
                    @Override public void showSuccess(String msg) {
                        SwingUtilities.invokeLater(() -> onRegisterSuccess(msg));
                    }
                    @Override public void resetForReconnect() { /* no-op */ }
                };
                return tempClient.connect(cb);
            }
            @Override protected void done() {
                try {
                    if (get()) {
                        showStatus("Registering…", TEXT_DIM);
                        tempClient.sendRegister(u, p);
                    } else {
                        setFormEnabled(true);
                        showError("Cannot reach server.");
                    }
                } catch (Exception ex) {
                    setFormEnabled(true);
                    showError("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ════════════════════════════════════════════════════════════════
    //  CALLBACKS
    // ════════════════════════════════════════════════════════════════

    public void onRegisterSuccess(String message) {
        if (tempClient != null) tempClient.disconnect();
        dispose();
        loginFrame.setVisible(true);
        loginFrame.showSuccess("Account created!  You can now sign in.");
    }

    public void onRegisterFailure(String reason) {
        if (tempClient != null) tempClient.disconnect();
        setFormEnabled(true);
        showError(reason);
    }

    public void showError(String msg) { showStatus(msg, ERROR_COL); }

    private void showStatus(String msg, Color c) {
        statusLabel.setText(msg);
        statusLabel.setForeground(c);
    }

    private void setFormEnabled(boolean on) {
        registerButton.setEnabled(on);
        backButton.setEnabled(on);
        usernameField.setEnabled(on);
        passwordField.setEnabled(on);
        confirmField.setEnabled(on);
    }
}
