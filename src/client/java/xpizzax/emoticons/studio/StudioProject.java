package xpizzax.emoticons.studio;

import java.util.ArrayList;
import java.util.List;

/** Serializable project container shared by recording and the integrated editor. */
public final class StudioProject {
    public int formatVersion = 1;
    public String name = "Untitled cinematic";
    public int durationTicks;
    public final List<StudioFrame> frames = new ArrayList<>();
}
