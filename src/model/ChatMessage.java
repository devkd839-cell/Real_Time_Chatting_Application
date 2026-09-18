package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ChatMessage.java
 * ----------------
 * This class represents a single chat message travelling between
 * a sender and a recipient.
 *
 * It carries:
 *   - the sender's username
 *   - the recipient's username
 *   - the message content  (may be encrypted ciphertext on the wire,
 *                           or plaintext after decryption on the client)
 *   - a timestamp generated at the moment the object is created
 *
 * The class is Serializable for the same reason as User.java —
 * so we can optionally persist or transmit it as an object.
 * In this application we actually transmit it as a formatted
 * String using MessageProtocol, but keeping Serializable is
 * good practice and costs nothing.
 */
public class ChatMessage implements java.io.Serializable {

    private static final long serialVersionUID = 2L;

    // Who is sending the message
    private String sender;

    // Who should receive the message.
    // "ALL" is a special value meaning broadcast to every connected user.
    private String recipient;

    // The actual text of the message.
    // When a message is on the wire (inside a socket) this field holds
    // the encrypted ciphertext (Base64-encoded).
    // Once the receiving client decrypts it, this field holds the
    // readable plain text that is displayed in the chat window.
    private String content;

    // Automatically set to the current date/time when the object is created.
    // Formatter: HH:mm:ss  (e.g. 14:35:07)
    private String timestamp;

    // Formatter used to generate human-readable timestamps
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // Constructor — timestamp is set automatically
    public ChatMessage(String sender, String recipient, String content) {
        this.sender    = sender;
        this.recipient = recipient;
        this.content   = content;
        // Record the exact moment this message was created
        this.timestamp = LocalDateTime.now().format(FORMATTER);
    }

    // --- Getters ---

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    // --- Setter for content only ---
    // After decryption on the client side, we replace the ciphertext
    // with the original plaintext using this setter.
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Returns a nicely formatted single line suitable for display
     * in the chat window.
     *
     * Example output:
     *   [14:35:07] Krishna → Rahul: Hello!
     */
    public String toDisplayString() {
        return "[" + timestamp + "] " + sender + " → " + recipient + ": " + content;
    }

    @Override
    public String toString() {
        return "ChatMessage{sender='" + sender + "', recipient='" + recipient +
               "', timestamp='" + timestamp + "'}";
    }
}
