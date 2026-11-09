package xpizzax.emoticons.client.update;

/**
 * Small thread-safe status bridge between the resource reload worker and
 * Minecraft's splash/loading overlay.
 */
public final class XpaUpdateStatus {
    private static volatile String message = "";

    private XpaUpdateStatus() {}

    public static void show(String value) {
        message = value == null ? "" : value;
    }

    public static void hide() {
        message = "";
    }

    public static String getMessage() {
        return message;
    }

    public static boolean isVisible() {
        return !message.isBlank();
    }
}
