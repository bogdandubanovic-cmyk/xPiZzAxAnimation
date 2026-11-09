package xpizzax.emoticons.client.update;

/** Thread-safe status shared by the resource reload worker and the splash screen. */
public final class XpaUpdateStatus {
    private static volatile String message = "";
    private static volatile float progress = -1.0f;

    private XpaUpdateStatus() {}

    public static void show(String value) {
        show(value, -1.0f);
    }

    public static void show(String value, float valueProgress) {
        message = value == null ? "" : value;
        progress = Math.max(-1.0f, Math.min(1.0f, valueProgress));
    }

    public static void hide() {
        message = "";
        progress = -1.0f;
    }

    public static String getMessage() {
        return message;
    }

    public static float getProgress() {
        return progress;
    }
}
