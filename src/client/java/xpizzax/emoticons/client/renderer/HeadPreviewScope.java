package xpizzax.emoticons.client.renderer;

import java.util.Arrays;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Hide the actual model's body only during the GUI pass, preserving EMF head/eyes. */
public final class HeadPreviewScope {
    private static final class Frame {
        final ModelPart[] parts = new ModelPart[10];
        final boolean[] wasVisible = new boolean[10];
        int size;
    }

    // Head preview renders every frame. Reuse small fixed frames instead of
    // allocating IdentityHashMap entries for all hidden parts on every render.
    private static Frame[] frames = {new Frame(), new Frame(), new Frame(), new Frame()};
    private static int depth;

    private HeadPreviewScope() {}

    public static void mask(PlayerEntityModel<?> model) {
        if (depth == frames.length) frames = Arrays.copyOf(frames, frames.length * 2);
        Frame frame = frames[depth];
        if (frame == null) frames[depth] = frame = new Frame();
        depth++;
        frame.size = 10;
        rememberAndHide(frame, 0, model.body);
        rememberAndHide(frame, 1, model.leftArm);
        rememberAndHide(frame, 2, model.rightArm);
        rememberAndHide(frame, 3, model.leftLeg);
        rememberAndHide(frame, 4, model.rightLeg);
        rememberAndHide(frame, 5, model.jacket);
        rememberAndHide(frame, 6, model.leftSleeve);
        rememberAndHide(frame, 7, model.rightSleeve);
        rememberAndHide(frame, 8, model.leftPants);
        rememberAndHide(frame, 9, model.rightPants);
    }

    private static void rememberAndHide(Frame frame, int index, ModelPart part) {
        frame.parts[index] = part;
        frame.wasVisible[index] = part.visible;
        part.visible = false;
    }

    public static void restore() {
        if (depth == 0) return;
        Frame frame = frames[--depth];
        for (int i = frame.size - 1; i >= 0; i--) {
            frame.parts[i].visible = frame.wasVisible[i];
            frame.parts[i] = null;
        }
        frame.size = 0;
    }
}
