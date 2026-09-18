package client;

/**
 * ServerCallback.java
 * -------------------
 * A simple interface that decouples ChatClient from the concrete
 * LoginFrame class.
 *
 * ChatClient holds a ServerCallback reference instead of a LoginFrame,
 * which means:
 *   - LoginFrame implements ServerCallback  (normal login)
 *   - A lightweight RegisterCallback can implement it too (registration)
 *   - No more fake LoginFrame subclass just to satisfy the API
 *
 * This eliminates the RegisterCallbackAdapter anti-pattern that was
 * accidentally opening a hidden LoginFrame window on every registration.
 */
public interface ServerCallback {

    /**
     * Called when the server confirms a successful login.
     * @param username  the authenticated username
     */
    void onLoginSuccess(String username);

    /**
     * Show an error message to the user.
     * @param message  the error text
     */
    void showError(String message);

    /**
     * Show a success/info message to the user.
     * @param message  the success text
     */
    void showSuccess(String message);

    /**
     * Called when the server connection drops unexpectedly.
     * Re-enables the login form so the user can try again.
     */
    void resetForReconnect();
}
