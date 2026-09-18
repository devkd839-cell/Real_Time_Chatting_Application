package client;

import util.Constants;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * LoginFrame.java  — redesigned
 * ─────────────────────────────
 * Modern split-panel login screen:
 *   LEFT  (40%) — decorative branded panel
 *   RIGHT (60%) — login / register form
 *
 * Multiple independent windows are supported: every call to
 * LoginFrame.main() (or opening a new window) creates a completely
 * separate LoginFrame + ChatClient pair, so several users can be
 * logged in simultaneously on the same machine.
 */
public class LoginFrame extends JFrame implements ServerCallback {

    // ── Form fields ─────────────────────────────────────────────────
    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JButton        loginButton;
    private JButton        gotoRegisterButton;
    private JLabel         statusLabel;

    // ── Networking ───────────────────────────────────────────────────
    private ChatClient chatClient;

    // ── Colours (local aliases for readability) ───────────────────────
    private static final Color BG_DARK    = new Color(18, 18, 28);
    private static final Color BG_PANEL   = new Color(28, 28, 42);
    private static final Color BG_FIELD   = new Color(38, 38, 56);
    private static final Color ACCENT     = new Color(88, 101, 242);
    private static final Color ACCENT2    = new Color(115, 138, 255);
    private static final Color TEXT       = new Color(220, 220, 235);
    private static final Color TEXT_DIM   = new Color(130, 130, 160);
    private static final Color ERROR_COL  = new Color(240,  71,  71);
    private static final Color SUCCESS_COL= new Color( 67, 181, 129);

    // ────────────────────────────────────────────────────────────────
    public LoginFrame() {
        setTitle(Constants.APP_TITLE + "  ·  Login");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(860, 560);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(null);
        setBackground(BG_DARK);
        buildUI();
    }

    // ════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        JPanel root = new JPanel(new GridLayout(1, 2));
        root.setBackground(BG_DARK);
        setContentPane(root);

        root.add(buildBrandPanel());
        root.add(buildFormPanel());
    }

    // ── LEFT — decorative brand panel ────────────────────────────────

    private JPanel buildBrandPanel() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                // Gradient background
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(55, 68, 195),
                        getWidth(), getHeight(), new Color(20, 20, 50));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Decorative circles
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillOval(-60, -60, 300, 300);
                g2.fillOval(getWidth() - 140, getHeight() - 120, 260, 260);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillOval(40, getHeight() - 160, 200, 200);
            }
        };
        panel.setLayout(new GridBagLayout());
        panel.setOpaque(false);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        // Lock icon (large)
        JLabel icon = new JLabel("🔒");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 56));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        // App name
        JLabel name = new JLabel("SecureChat");
        name.setFont(new Font("Segoe UI", Font.BOLD, 32));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Tagline
        JLabel tag = new JLabel("<html><div style='text-align:center'>"
                + "Private · Encrypted · Real-time</div></html>");
        tag.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tag.setForeground(new Color(200, 210, 255));
        tag.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Feature pills
        inner.add(icon);
        inner.add(Box.createVerticalStrut(14));
        inner.add(name);
        inner.add(Box.createVerticalStrut(10));
        inner.add(tag);
        inner.add(Box.createVerticalStrut(28));
        inner.add(featurePill("🔐  AES-256-GCM Encryption"));
        inner.add(Box.createVerticalStrut(8));
        inner.add(featurePill("👥  Multiple Users Simultaneously"));
        inner.add(Box.createVerticalStrut(8));
        inner.add(featurePill("⚡  Real-Time Messaging"));
        inner.add(Box.createVerticalStrut(8));
        inner.add(featurePill("🛡  Password Hashing (SHA-256)"));

        panel.add(inner);
        return panel;
    }

    private JLabel featurePill(String text) {
        JLabel lbl = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                super.paintComponent(g);
            }
        };
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(new Color(220, 225, 255));
        lbl.setBorder(new EmptyBorder(6, 16, 6, 16));
        lbl.setOpaque(false);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    // ── RIGHT — login form ───────────────────────────────────────────

    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG_PANEL);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setMaximumSize(new Dimension(380, Integer.MAX_VALUE));
        form.setPreferredSize(new Dimension(340, 420));

        // Title
        JLabel title = centeredLabel("Welcome back", 22, Font.BOLD, TEXT);
        JLabel subtitle = centeredLabel("Sign in to continue", 13, Font.PLAIN, TEXT_DIM);

        // Fields
        JLabel userLbl = fieldLabel("Username");
        usernameField  = styledField("Enter your username");

        JLabel passLbl = fieldLabel("Password");
        passwordField  = styledPasswordField("Enter your password");

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(ERROR_COL);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        // Login button
        loginButton = accentButton("Sign In", ACCENT);

        // Divider
        JLabel divider = centeredLabel("─────── or ───────", 11, Font.PLAIN, TEXT_DIM);

        // Register button
        gotoRegisterButton = ghostButton("Create a new account");

        // Assemble
        form.add(title);
        form.add(Box.createVerticalStrut(4));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(28));
        form.add(userLbl);
        form.add(Box.createVerticalStrut(6));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(16));
        form.add(passLbl);
        form.add(Box.createVerticalStrut(6));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(10));
        form.add(statusLabel);
        form.add(Box.createVerticalStrut(14));
        form.add(loginButton);
        form.add(Box.createVerticalStrut(16));
        form.add(divider);
        form.add(Box.createVerticalStrut(12));
        form.add(gotoRegisterButton);

        // Wire events
        loginButton.addActionListener(e -> attemptLogin());
        gotoRegisterButton.addActionListener(e -> openRegister());
        passwordField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) attemptLogin();
            }
        });
        usernameField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) attemptLogin();
            }
        });

        outer.add(form);
        return outer;
    }

    // ════════════════════════════════════════════════════════════════
    //  LOGIN LOGIC
    // ════════════════════════════════════════════════════════════════

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) { showError("Please enter your username."); return; }
        if (password.isEmpty()) { showError("Please enter your password."); return; }

        setFormEnabled(false);
        showStatus("Connecting…", TEXT_DIM);

        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                chatClient = new ChatClient();
                return chatClient.connect(LoginFrame.this);
            }
            @Override protected void done() {
                try {
                    if (get()) {
                        showStatus("Authenticating…", TEXT_DIM);
                        chatClient.sendLogin(username, password);
                    } else {
                        setFormEnabled(true);
                        showError("Cannot reach server. Is it running?");
                    }
                } catch (Exception ex) {
                    setFormEnabled(true);
                    showError("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ════════════════════════════════════════════════════════════════
    //  CALLBACKS  (called by ChatClient via SwingUtilities.invokeLater)
    // ════════════════════════════════════════════════════════════════

    public void onLoginSuccess(String username) {
        setVisible(false);
        dispose();
    }

    public void showError(String msg) {
        showStatus(msg, ERROR_COL);
        setFormEnabled(true);
    }

    public void showSuccess(String msg) {
        showStatus(msg, SUCCESS_COL);
        setFormEnabled(true);
    }

    @Override
    public void resetForReconnect() {
        setFormEnabled(true);
        setVisible(true);
        showError("Disconnected from server.");
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════

    private void openRegister() {
        new RegisterFrame(this).setVisible(true);
    }

    private void showStatus(String msg, Color c) {
        statusLabel.setText(msg);
        statusLabel.setForeground(c);
    }

    private void setFormEnabled(boolean on) {
        loginButton.setEnabled(on);
        gotoRegisterButton.setEnabled(on);
        usernameField.setEnabled(on);
        passwordField.setEnabled(on);
    }

    // ── Component factories ──────────────────────────────────────────

    static JLabel centeredLabel(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Segoe UI", style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, size + 10));
        return l;
    }

    static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(170, 170, 200));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JTextField styledField(String placeholder) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(120, 120, 150));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    Insets ins = getInsets();
                    g2.drawString(placeholder, ins.left, getHeight() / 2 + getFont().getSize() / 2 - 2);
                }
            }
        };
        styleInputField(f);
        return f;
    }

    static JPasswordField styledPasswordField(String placeholder) {
        JPasswordField f = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getPassword().length == 0 && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(120, 120, 150));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    Insets ins = getInsets();
                    g2.drawString(placeholder, ins.left, getHeight() / 2 + getFont().getSize() / 2 - 2);
                }
            }
        };
        styleInputField(f);
        return f;
    }

    private static void styleInputField(JTextField f) {
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        f.setBackground(new Color(38, 38, 56));
        f.setForeground(new Color(220, 220, 235));
        f.setCaretColor(new Color(88, 101, 242));
        f.setSelectionColor(new Color(88, 101, 242, 120));
        f.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(new Color(60, 65, 100), 1, 10),
                new EmptyBorder(10, 14, 10, 14)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.setOpaque(true);

        // Highlight border on focus
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(new Color(88, 101, 242), 2, 10),
                        new EmptyBorder(10, 14, 10, 14)));
            }
            @Override public void focusLost(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(new Color(60, 65, 100), 1, 10),
                        new EmptyBorder(10, 14, 10, 14)));
            }
        });
    }

    static JButton accentButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? getBackground() : getBackground().darker());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    static JButton ghostButton(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(88, 101, 242, 40));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setForeground(new Color(88, 101, 242));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        return btn;
    }

    // ════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ════════════════════════════════════════════════════════════════

    /**
     * Launch a new independent client window.
     * You can run this multiple times simultaneously to simulate multiple users.
     *
     * Compile from SecureChatApplication/:
     *   javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java -Path src | % FullName)
     *
     * Run server first, then run this for each user:
     *   java -cp out client.LoginFrame
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            // Smooth fonts on Windows
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}


// ────────────────────────────────────────────────────────────────────
//  RoundBorder  — helper that draws a rounded rectangle border
// ────────────────────────────────────────────────────────────────────
class RoundBorder extends AbstractBorder {
    private final Color color;
    private final int   thickness;
    private final int   radius;

    RoundBorder(Color color, int thickness, int radius) {
        this.color     = color;
        this.thickness = thickness;
        this.radius    = radius;
    }

    @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        g2.draw(new RoundRectangle2D.Float(
                x + thickness / 2f, y + thickness / 2f,
                w - thickness, h - thickness, radius, radius));
        g2.dispose();
    }

    @Override public Insets getBorderInsets(Component c) {
        return new Insets(thickness, thickness, thickness, thickness);
    }
}
