package client;

import util.Constants;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ChatFrame.java  — redesigned
 * ─────────────────────────────
 * Modern Discord-inspired chat window.
 *
 * LAYOUT
 * ──────
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ TITLE BAR: 🔒 SecureChat     [username]     [●Connected] [LOGOUT]│
 * ├─────────────────┬────────────────────────────────────────────────┤
 * │ SIDEBAR         │  CHAT HEADER: "Chat with [recipient]"          │
 * │                 ├────────────────────────────────────────────────┤
 * │  ● krishna      │                                                │
 * │  ● rahul   ←──  │   [bubble] Krishna: Hello!          14:01     │
 * │  ● amit         │               [bubble] You: Hi there! 14:02   │
 * │  ● priya        │   [bubble] Krishna: How are you?    14:02     │
 * │                 │                                                │
 * │  ── INFO ──     ├────────────────────────────────────────────────┤
 * │  3 online       │  [Message input field…]         [SEND ➤]      │
 * └─────────────────┴────────────────────────────────────────────────┘
 *
 * Each message is rendered as a styled bubble:
 *   Incoming  → left-aligned, dark bubble, sender name shown
 *   Outgoing  → right-aligned, accent-colour bubble, "You" label
 *   System    → centred, italic, dim
 */
public class ChatFrame extends JFrame {

    // ── Networking ───────────────────────────────────────────────────
    private final ChatClient chatClient;
    private final String     myUsername;

    // ── State ────────────────────────────────────────────────────────
    private String selectedRecipient = null;

    // ── Components ───────────────────────────────────────────────────
    private JPanel                chatBubblePanel;   // holds message bubbles
    private JScrollPane           chatScroll;
    private DefaultListModel<String> userListModel;
    private JList<String>         userList;
    private JTextField            messageField;
    private JButton               sendButton;
    private JLabel                chatHeaderLabel;
    private JLabel                onlineCountLabel;
    private JLabel                connectionDot;

    // ── Palette ──────────────────────────────────────────────────────
    static final Color BG_MAIN      = new Color(18, 18, 28);
    static final Color BG_SIDEBAR   = new Color(22, 22, 35);
    static final Color BG_CHAT      = new Color(25, 25, 38);
    static final Color BG_HEADER    = new Color(20, 20, 32);
    static final Color BG_INPUT     = new Color(30, 30, 46);
    static final Color BG_FIELD     = new Color(38, 38, 58);
    static final Color ACCENT       = new Color(88, 101, 242);
    static final Color BUBBLE_IN    = new Color(38, 40, 62);
    static final Color BUBBLE_OUT   = new Color(68, 79, 210);
    static final Color TEXT_MAIN    = new Color(220, 220, 235);
    static final Color TEXT_DIM     = new Color(120, 130, 160);
    static final Color TEXT_TIME    = new Color(100, 105, 140);
    static final Color ONLINE_GREEN = new Color(67, 181, 129);
    static final Color SELECTED_BG  = new Color(45, 50, 80);
    static final Color HOVER_BG     = new Color(35, 38, 58);
    static final Color ERROR        = new Color(240, 71, 71);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // ════════════════════════════════════════════════════════════════
    public ChatFrame(ChatClient chatClient, String myUsername) {
        this.chatClient  = chatClient;
        this.myUsername  = myUsername;

        setTitle(Constants.APP_TITLE + "  ·  " + myUsername);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 680);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);

        buildUI();
        setupCloseHandler();
        chatClient.requestUserList();
    }

    // ════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_MAIN);
        setContentPane(root);

        root.add(buildTitleBar(),   BorderLayout.NORTH);
        root.add(buildSidebar(),    BorderLayout.WEST);
        root.add(buildChatArea(),   BorderLayout.CENTER);
    }

    // ── TITLE BAR ────────────────────────────────────────────────────

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 55, 90)),
                new EmptyBorder(10, 18, 10, 18)));

        // Left: branding
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        JLabel iconLbl = new JLabel("🔒");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        JLabel appName = new JLabel("SecureChat");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 17));
        appName.setForeground(TEXT_MAIN);
        left.add(iconLbl);
        left.add(appName);

        // Centre: logged-in badge
        JLabel userBadge = new JLabel("Signed in as  " + myUsername);
        userBadge.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userBadge.setForeground(TEXT_DIM);
        userBadge.setHorizontalAlignment(SwingConstants.CENTER);

        // Right: connection + logout
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        connectionDot = new JLabel("● Connected");
        connectionDot.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        connectionDot.setForeground(ONLINE_GREEN);

        JButton logoutBtn = makeLogoutButton();
        right.add(connectionDot);
        right.add(logoutBtn);

        bar.add(left,      BorderLayout.WEST);
        bar.add(userBadge, BorderLayout.CENTER);
        bar.add(right,     BorderLayout.EAST);
        return bar;
    }

    private JButton makeLogoutButton() {
        JButton btn = new JButton("⏻  Logout") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover()
                        ? new Color(200, 50, 50) : new Color(100, 35, 35));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(new Color(255, 120, 120));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 12, 6, 12));
        btn.addActionListener(e -> performLogout());
        return btn;
    }

    // ── SIDEBAR (online users) ────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(40, 45, 75)));

        // Section header
        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setBackground(BG_SIDEBAR);
        sectionHeader.setBorder(new EmptyBorder(14, 14, 10, 14));

        JLabel onlineTitle = new JLabel("ONLINE");
        onlineTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        onlineTitle.setForeground(TEXT_DIM);

        onlineCountLabel = new JLabel("0");
        onlineCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        onlineCountLabel.setForeground(ONLINE_GREEN);

        sectionHeader.add(onlineTitle,     BorderLayout.WEST);
        sectionHeader.add(onlineCountLabel, BorderLayout.EAST);

        // User list
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setForeground(TEXT_MAIN);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setSelectionBackground(new Color(0, 0, 0, 0)); // handled by renderer
        userList.setSelectionForeground(TEXT_MAIN);
        userList.setFixedCellHeight(50);
        userList.setBorder(null);
        userList.setCellRenderer(new UserListRenderer());

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = userList.getSelectedValue();
                if (sel != null) setRecipient(sel);
            }
        });

        JScrollPane listScroll = new JScrollPane(userList);
        listScroll.setBorder(null);
        listScroll.setBackground(BG_SIDEBAR);
        listScroll.getViewport().setBackground(BG_SIDEBAR);
        listScroll.getVerticalScrollBar().setBackground(BG_SIDEBAR);

        // Bottom info panel
        JPanel bottomInfo = new JPanel();
        bottomInfo.setBackground(new Color(18, 18, 30));
        bottomInfo.setLayout(new BoxLayout(bottomInfo, BoxLayout.Y_AXIS));
        bottomInfo.setBorder(new EmptyBorder(10, 14, 14, 14));

        JLabel encLabel = new JLabel("🔐 AES-256-GCM Encrypted");
        encLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        encLabel.setForeground(new Color(90, 100, 140));
        encLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel portLabel = new JLabel("⚡ Port " + Constants.PORT);
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        portLabel.setForeground(new Color(90, 100, 140));
        portLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottomInfo.add(encLabel);
        bottomInfo.add(Box.createVerticalStrut(4));
        bottomInfo.add(portLabel);

        sidebar.add(sectionHeader, BorderLayout.NORTH);
        sidebar.add(listScroll,    BorderLayout.CENTER);
        sidebar.add(bottomInfo,    BorderLayout.SOUTH);
        return sidebar;
    }

    // Custom renderer for the user list
    private class UserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean hasFocus) {

            JPanel cell = new JPanel(new BorderLayout(10, 0));
            cell.setOpaque(true);
            cell.setBorder(new EmptyBorder(8, 12, 8, 12));

            String username = (String) value;
            boolean sel = username.equals(selectedRecipient);

            cell.setBackground(sel ? SELECTED_BG : BG_SIDEBAR);

            // Avatar circle
            JLabel avatar = new JLabel(String.valueOf(username.charAt(0)).toUpperCase()) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Hash the username to pick a consistent colour
                    int hue = Math.abs(username.hashCode()) % 360;
                    Color avatarColor = Color.getHSBColor(hue / 360f, 0.55f, 0.75f);
                    g2.setColor(avatarColor);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            avatar.setFont(new Font("Segoe UI", Font.BOLD, 14));
            avatar.setForeground(Color.WHITE);
            avatar.setHorizontalAlignment(SwingConstants.CENTER);
            avatar.setPreferredSize(new Dimension(34, 34));
            avatar.setOpaque(false);

            // Name + status
            JPanel textBlock = new JPanel();
            textBlock.setOpaque(false);
            textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));

            JLabel nameLbl = new JLabel(username);
            nameLbl.setFont(new Font("Segoe UI", sel ? Font.BOLD : Font.PLAIN, 13));
            nameLbl.setForeground(sel ? TEXT_MAIN : new Color(190, 190, 215));

            JLabel statusLbl = new JLabel("● online");
            statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            statusLbl.setForeground(ONLINE_GREEN);

            textBlock.add(nameLbl);
            textBlock.add(statusLbl);

            cell.add(avatar,    BorderLayout.WEST);
            cell.add(textBlock, BorderLayout.CENTER);

            // hover effect
            cell.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    if (!sel) cell.setBackground(HOVER_BG);
                }
                @Override public void mouseExited(MouseEvent e) {
                    if (!sel) cell.setBackground(BG_SIDEBAR);
                }
            });
            return cell;
        }
    }

    // ── CHAT AREA ─────────────────────────────────────────────────────

    private JPanel buildChatArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(BG_CHAT);

        area.add(buildChatHeader(), BorderLayout.NORTH);
        area.add(buildMessageArea(), BorderLayout.CENTER);
        area.add(buildInputBar(),   BorderLayout.SOUTH);
        return area;
    }

    private JPanel buildChatHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(45, 50, 85)),
                new EmptyBorder(12, 18, 12, 18)));

        chatHeaderLabel = new JLabel("Select a user to start chatting");
        chatHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatHeaderLabel.setForeground(TEXT_MAIN);

        JLabel hintLbl = new JLabel("Private · Encrypted");
        hintLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hintLbl.setForeground(TEXT_DIM);

        header.add(chatHeaderLabel, BorderLayout.WEST);
        header.add(hintLbl,        BorderLayout.EAST);
        return header;
    }

    private JScrollPane buildMessageArea() {
        // chatBubblePanel uses BoxLayout(Y_AXIS) — each bubble is a JPanel added vertically
        chatBubblePanel = new JPanel();
        chatBubblePanel.setLayout(new BoxLayout(chatBubblePanel, BoxLayout.Y_AXIS));
        chatBubblePanel.setBackground(BG_CHAT);
        chatBubblePanel.setBorder(new EmptyBorder(10, 8, 10, 8));

        chatScroll = new JScrollPane(chatBubblePanel);
        chatScroll.setBorder(null);
        chatScroll.setBackground(BG_CHAT);
        chatScroll.getViewport().setBackground(BG_CHAT);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        styleScrollBar(chatScroll.getVerticalScrollBar());

        addSystemBubble("Welcome, " + myUsername + "! Pick a user from the sidebar to start a private encrypted chat.");
        return chatScroll;
    }

    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(BG_INPUT);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 50, 85)),
                new EmptyBorder(12, 16, 12, 16)));

        // Message field
        messageField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(TEXT_DIM);
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    Insets ins = getInsets();
                    g2.drawString("Type a message…", ins.left,
                            getHeight() / 2 + getFont().getSize() / 2 - 2);
                }
            }
        };
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setBackground(BG_FIELD);
        messageField.setForeground(TEXT_MAIN);
        messageField.setCaretColor(ACCENT);
        messageField.setSelectionColor(new Color(88, 101, 242, 120));
        messageField.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(new Color(55, 60, 100), 1, 10),
                new EmptyBorder(10, 14, 10, 14)));
        messageField.setEnabled(false);
        messageField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendMessage();
            }
        });
        messageField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                messageField.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(ACCENT, 2, 10),
                        new EmptyBorder(10, 14, 10, 14)));
            }
            @Override public void focusLost(FocusEvent e) {
                messageField.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(new Color(55, 60, 100), 1, 10),
                        new EmptyBorder(10, 14, 10, 14)));
            }
        });

        // Send button
        sendButton = new JButton("Send  ➤") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isEnabled()
                        ? (getModel().isRollover() ? ACCENT.brighter() : ACCENT)
                        : new Color(55, 60, 90);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setOpaque(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.setPreferredSize(new Dimension(110, 44));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        bar.add(messageField, BorderLayout.CENTER);
        bar.add(sendButton,   BorderLayout.EAST);
        return bar;
    }

    // ════════════════════════════════════════════════════════════════
    //  SEND
    // ════════════════════════════════════════════════════════════════

    private void sendMessage() {
        if (selectedRecipient == null) return;
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        chatClient.sendChatMessage(selectedRecipient, text);
        addOutgoingBubble(selectedRecipient, text);
        messageField.setText("");
        messageField.requestFocus();
    }

    // ════════════════════════════════════════════════════════════════
    //  RECIPIENT SELECTION
    // ════════════════════════════════════════════════════════════════

    private void setRecipient(String username) {
        selectedRecipient = username;
        chatHeaderLabel.setText("💬  " + username);
        chatHeaderLabel.setForeground(TEXT_MAIN);
        messageField.setEnabled(true);
        sendButton.setEnabled(true);
        messageField.requestFocus();
        userList.repaint(); // refresh renderer to show selection highlight
    }

    // ════════════════════════════════════════════════════════════════
    //  PUBLIC CALLBACKS  (called by ChatClient via invokeLater)
    // ════════════════════════════════════════════════════════════════

    /**
     * Display an incoming message as a left-aligned chat bubble.
     */
    public void displayMessage(String sender, String plaintext) {
        addIncomingBubble(sender, plaintext);
    }

    /**
     * Refresh the online user list in the sidebar.
     */
    public void updateOnlineUsers(String[] users) {
        userListModel.clear();
        int count = 0;
        for (String u : users) {
            if (!u.trim().equals(myUsername)) {
                userListModel.addElement(u.trim());
                count++;
            }
        }
        onlineCountLabel.setText(String.valueOf(count));

        // If selected recipient went offline, reset
        if (selectedRecipient != null && !userListModel.contains(selectedRecipient)) {
            addSystemBubble(selectedRecipient + " went offline.");
            selectedRecipient = null;
            chatHeaderLabel.setText("Select a user to start chatting");
            chatHeaderLabel.setForeground(TEXT_DIM);
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
        }
    }

    /** Show an error dialog. */
    public void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ════════════════════════════════════════════════════════════════
    //  BUBBLE BUILDERS
    // ════════════════════════════════════════════════════════════════

    /** Incoming message — left side, dark bubble */
    private void addIncomingBubble(String sender, String text) {
        String time = LocalDateTime.now().format(TIME_FMT);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Avatar
        JLabel av = makeAvatarLabel(sender);

        // Bubble content
        JPanel bubble = makeBubble(text, sender, time, BUBBLE_IN, TEXT_MAIN, false);

        JPanel combined = new JPanel();
        combined.setOpaque(false);
        combined.setLayout(new BoxLayout(combined, BoxLayout.X_AXIS));
        combined.setBorder(new EmptyBorder(2, 6, 2, 60));
        combined.add(av);
        combined.add(Box.createHorizontalStrut(8));
        combined.add(bubble);
        combined.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        chatBubblePanel.add(combined);
        scrollToBottom();
    }

    /** Outgoing message — right side, accent bubble */
    private void addOutgoingBubble(String recipient, String text) {
        String time = LocalDateTime.now().format(TIME_FMT);
        JPanel combined = new JPanel();
        combined.setOpaque(false);
        combined.setLayout(new BoxLayout(combined, BoxLayout.X_AXIS));
        combined.setBorder(new EmptyBorder(2, 60, 2, 6));
        combined.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel bubble = makeBubble(text, "You → " + recipient, time, BUBBLE_OUT, Color.WHITE, true);

        combined.add(Box.createHorizontalGlue());
        combined.add(bubble);
        combined.add(Box.createHorizontalStrut(8));
        combined.add(makeAvatarLabel(myUsername));

        chatBubblePanel.add(combined);
        scrollToBottom();
    }

    /** System notification — centred, no bubble */
    private void addSystemBubble(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lbl.setForeground(TEXT_DIM);
        lbl.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(new Color(50, 55, 85), 1, 12),
                new EmptyBorder(4, 12, 4, 12)));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(28, 30, 50));

        row.add(lbl);
        chatBubblePanel.add(row);
        scrollToBottom();
    }

    /** Build a rounded message bubble panel */
    private JPanel makeBubble(String text, String senderLabel,
                               String time, Color bg, Color fg, boolean outgoing) {
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
            }
        };
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(bg);
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Sender/label row
        JLabel senderLbl = new JLabel(senderLabel);
        senderLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        senderLbl.setForeground(outgoing ? new Color(190, 200, 255) : new Color(140, 160, 255));
        senderLbl.setAlignmentX(outgoing ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        // Message text — supports wrapping via HTML
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel msgLbl = new JLabel("<html><div style='max-width:320px; word-wrap:break-word;'>"
                + escaped + "</div></html>");
        msgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        msgLbl.setForeground(fg);
        msgLbl.setAlignmentX(outgoing ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        // Timestamp
        JLabel timeLbl = new JLabel(time);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(outgoing ? new Color(180, 190, 255, 180) : TEXT_TIME);
        timeLbl.setAlignmentX(outgoing ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        bubble.add(senderLbl);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(msgLbl);
        bubble.add(Box.createVerticalStrut(3));
        bubble.add(timeLbl);

        return bubble;
    }

    /** Circular avatar label with initials and colour derived from username */
    private JLabel makeAvatarLabel(String username) {
        JLabel av = new JLabel(String.valueOf(username.charAt(0)).toUpperCase()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int hue = Math.abs(username.hashCode()) % 360;
                g2.setColor(Color.getHSBColor(hue / 360f, 0.55f, 0.72f));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        av.setPreferredSize(new Dimension(32, 32));
        av.setMinimumSize(new Dimension(32, 32));
        av.setMaximumSize(new Dimension(32, 32));
        av.setFont(new Font("Segoe UI", Font.BOLD, 13));
        av.setForeground(Color.WHITE);
        av.setHorizontalAlignment(SwingConstants.CENTER);
        av.setOpaque(false);
        av.setAlignmentY(Component.TOP_ALIGNMENT);
        return av;
    }

    // ════════════════════════════════════════════════════════════════
    //  LOGOUT / CLOSE
    // ════════════════════════════════════════════════════════════════

    private void performLogout() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Sign out of " + myUsername + "?",
                "Logout", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            chatClient.sendLogout();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    private void setupCloseHandler() {
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { performLogout(); }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            chatBubblePanel.revalidate();
            chatBubblePanel.repaint();
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void styleScrollBar(JScrollBar bar) {
        bar.setBackground(BG_CHAT);
        bar.setForeground(new Color(55, 60, 90));
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor  = new Color(65, 70, 110);
                trackColor  = BG_CHAT;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }
}
