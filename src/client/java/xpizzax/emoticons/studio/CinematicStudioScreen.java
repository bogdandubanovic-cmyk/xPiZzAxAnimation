package xpizzax.emoticons.studio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xpizzax.emoticons.studio.CinematicStudio;
import xpizzax.unified.ui.ForegroundScreen;

import java.util.Locale;

/**
 * A compact BBS-inspired stage editor: the live Minecraft world is the viewport,
 * with a part picker, transform inspector and take timeline around it.
 */
public final class CinematicStudioScreen extends ForegroundScreen {
    private static final String[] BONE_IDS = {
            "head", "body", "low_body", "left_arm", "low_left_arm",
            "right_arm", "low_right_arm", "left_leg", "low_left_leg",
            "right_leg", "low_leg_right", "eyes"
    };
    private static final String[] BONE_LABELS = {
            "Head", "Body", "Waist", "Left arm", "Left forearm",
            "Right arm", "Right forearm", "Left leg", "Left lower leg",
            "Right leg", "Right lower leg", "Eyes / gaze"
    };

    private static final int PANEL = 0xD90D1421;
    private static final int PANEL_ALT = 0xDF172235;
    private static final int BORDER = 0xFF39475D;
    private static final int ACCENT = 0xFFB56BFF;
    private static final int CYAN = 0xFF69D9F2;
    private static final int TEXT = 0xFFF0F4FA;
    private static final int MUTED = 0xFF9BA9BC;

    private final Screen parent;
    private String selectedBone = "head";
    private String mode = "rotate";
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int timelineY, trackX, trackW;
    private int activeSlider = -1;
    private float lastSliderValue;
    private boolean cameraDragging;

    public CinematicStudioScreen(Screen parent) {
        super(Text.literal("xPiZzAxAnimation — Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        leftX = 12; leftY = 44; leftW = 142; leftH = height - 158;
        rightW = 190; rightX = width - rightW - 12; rightY = 44; rightH = height - 158;
        timelineY = height - 104;
        trackX = 104; trackW = Math.max(80, width - 126);

        int x = Math.max(12, width - 485);
        int y = 12;
        addButton(CinematicStudio.isRecording() ? "Stop" : "● Record", x, y, 66,
                b -> { CinematicStudio.toggleRecording(MinecraftClient.getInstance()); clearAndInit(); });
        x += 70;
        addButton("Keyframe", x, y, 68, b -> CinematicStudio.addPoseKeyframe());
        x += 72;
        addButton(CinematicStudio.isPreviewPlaying() ? "Pause" : "Play", x, y, 52,
                b -> { CinematicStudio.togglePreviewPlayback(); clearAndInit(); });
        x += 56;
        addButton("Save", x, y, 48, b -> CinematicStudio.save());
        x += 52;
        addButton("Open", x, y, 48, b -> CinematicStudio.openProject());
        x += 52;
        addButton("Folder", x, y, 54, b -> CinematicStudio.openProjectDirectory());
        x += 58;
        addButton("×", x, y, 28, b -> close());
    }

    private void addButton(String label, int x, int y, int w, ButtonWidget.PressAction action) {
        if (x < 0 || x + w > width - 6 || y < 0 || y + 20 > height) return;
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, w, 20).build());
    }

    @Override
    protected void renderForeground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the 3D world fully visible. Only the editor docks are translucent.
        panel(context, 12, 8, width - 24, 27, PANEL);
        panel(context, leftX, leftY, leftW, leftH, PANEL);
        panel(context, rightX, rightY, rightW, rightH, PANEL);
        panel(context, 12, timelineY, width - 24, 92, PANEL_ALT);

        context.drawTextWithShadow(textRenderer, "xPiZzAxAnimation  /  STUDIO",
                22, 17, TEXT);
        context.drawTextWithShadow(textRenderer,
                "Right mouse + drag: look   ·   WASD: move   ·   Space / Shift: up / down   ·   Ctrl: faster",
                190, 17, MUTED);

        drawPartsPanel(context, mouseX, mouseY);
        drawInspector(context, mouseX, mouseY);
        drawTimeline(context);

        if (CinematicStudio.isRecording()) {
            context.fill(22, 40, 28, 46, 0xFFFF5555);
            context.drawTextWithShadow(textRenderer, "REC", 34, 39, 0xFFFF7777);
        } else {
            context.drawTextWithShadow(textRenderer, "LIVE 3D VIEWPORT", 22, 39, CYAN);
        }
        context.drawTextWithShadow(textRenderer,
                "The character stays in the world behind this interface. ReplayMod camera/playback is left alone.",
                22, height - 12, MUTED);
        renderWidgets(context, mouseX, mouseY, delta);
    }

    private void drawPartsPanel(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, "CHARACTER", leftX + 10, leftY + 10, CYAN);
        int rowY = leftY + 30;
        for (int i = 0; i < BONE_IDS.length; i++) {
            String id = BONE_IDS[i];
            int y = rowY + i * 23;
            boolean selected = id.equals(selectedBone);
            int color = selected ? 0xAA7846A9 : (mouseX >= leftX + 5 && mouseX <= leftX + leftW - 5
                    && mouseY >= y - 2 && mouseY < y + 19 ? 0x665C718D : 0x33202D40);
            context.fill(leftX + 6, y - 2, leftX + leftW - 6, y + 19, color);
            context.drawTextWithShadow(textRenderer, BONE_LABELS[i], leftX + 12, y + 4,
                    selected ? 0xFFFFFFFF : 0xFFD2DCE9);
        }
    }

    private void drawInspector(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, "TRANSFORM", rightX + 10, rightY + 10, CYAN);
        context.drawTextWithShadow(textRenderer, labelForBone(selectedBone), rightX + 10, rightY + 28, TEXT);

        int tabY = rightY + 49;
        drawTab(context, "Rotate", "rotate", rightX + 7, tabY, 56, mouseX, mouseY);
        drawTab(context, "Move", "move", rightX + 66, tabY, 52, mouseX, mouseY);
        drawTab(context, "Scale", "scale", rightX + 121, tabY, 60, mouseX, mouseY);

        for (int i = 0; i < 3; i++) {
            int y = rightY + 91 + i * 47;
            String axis = axisForSlider(i);
            String label = sliderLabel(i);
            float value = sliderValue(i);
            float min = sliderMin(i), max = sliderMax(i);
            int barX = rightX + 12, barW = rightW - 24;
            int barY = y + 21;
            int knobX = barX + Math.round((value - min) / (max - min) * barW);
            context.drawTextWithShadow(textRenderer, label, barX, y + 1, TEXT);
            context.drawTextWithShadow(textRenderer, format(value), rightX + rightW - 48, y + 1, MUTED);
            context.fill(barX, barY, barX + barW, barY + 4, BORDER);
            context.fill(barX, barY, Math.max(barX + 1, knobX), barY + 4, ACCENT);
            context.fill(knobX - 3, barY - 3, knobX + 4, barY + 8, 0xFFF2E8FF);
        }

        if ("eyes".equals(selectedBone)) {
            int expressionY = rightY + 242;
            context.drawTextWithShadow(textRenderer, "EXPRESSION", rightX + 10, expressionY, CYAN);
            drawExpression(context, "Normal", "normal", rightX + 8, expressionY + 18, mouseX, mouseY);
            drawExpression(context, "Blink", "blink", rightX + 66, expressionY + 18, mouseX, mouseY);
            drawExpression(context, "Happy", "happy", rightX + 124, expressionY + 18, mouseX, mouseY);
            context.drawTextWithShadow(textRenderer, "Gaze values are stored per keyframe.",
                    rightX + 10, expressionY + 51, MUTED);
        } else {
            context.drawTextWithShadow(textRenderer, "Drag a slider to pose this part.",
                    rightX + 10, rightY + 245, MUTED);
            context.drawTextWithShadow(textRenderer, "Edits add a keyframe automatically.",
                    rightX + 10, rightY + 260, MUTED);
        }
        context.drawTextWithShadow(textRenderer, "Selected: " + labelForBone(selectedBone),
                rightX + 10, rightY + rightH - 26, MUTED);
    }

    private void drawTimeline(DrawContext context) {
        context.drawTextWithShadow(textRenderer, "TAKE", 22, timelineY + 8, TEXT);
        StudioProject project = CinematicStudio.project();
        context.drawTextWithShadow(textRenderer, project.frames.size() + " samples  ·  "
                + project.durationTicks + " ticks", 22, timelineY + 24, MUTED);
        String[] lanes = {"CAMERA", "BODY", "EMOTECRAFT", "EYES"};
        int firstTrackY = timelineY + 17;
        for (int lane = 0; lane < lanes.length; lane++) {
            int y = firstTrackY + lane * 17;
            context.drawTextWithShadow(textRenderer, lanes[lane], 22, y, laneColor(lane));
            context.fill(trackX, y + 5, trackX + trackW, y + 7, BORDER);
        }
        if (project.frames.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Record a take or add a pose to start editing.",
                    trackX, firstTrackY + 10, MUTED);
            return;
        }
        int count = project.frames.size();
        int stride = Math.max(1, (count + 250) / 250);
        for (int i = 0; i < count; i += stride) {
            int px = trackX + (int)((long)i * Math.max(1, trackW - 2) / Math.max(1, count - 1));
            StudioFrame frame = project.frames.get(i);
            for (int lane = 0; lane < 4; lane++) {
                int y = firstTrackY + lane * 17;
                boolean visible = lane == 0 || (lane == 1 && !frame.bones.isEmpty())
                        || (lane == 2 && frame.emotecraftPose)
                        || (lane == 3 && frame.eyes != null);
                if (visible) context.fill(px, y, px + 2, y + 12,
                        i == CinematicStudio.selectedFrameIndex() ? 0xFFFFD36A : laneColor(lane));
            }
        }
        int selected = CinematicStudio.selectedFrameIndex();
        if (selected >= 0 && selected < count) {
            int px = trackX + (int)((long)selected * Math.max(1, trackW - 2) / Math.max(1, count - 1));
            context.fill(px - 1, firstTrackY - 4, px + 2, firstTrackY + 64, 0xFFFFD36A);
            context.drawTextWithShadow(textRenderer, "Frame " + selected + " / " + (count - 1),
                    trackX, timelineY + 78, MUTED);
        }
    }

    private void drawTab(DrawContext context, String label, String value, int x, int y, int w, int mx, int my) {
        boolean selected = mode.equals(value);
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + 20;
        context.fill(x, y, x + w, y + 20, selected ? 0xAA7846A9 : hovered ? 0x775C718D : 0x55313F55);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + 6, TEXT);
    }

    private void drawExpression(DrawContext context, String label, String value, int x, int y, int mx, int my) {
        boolean hovered = mx >= x && mx <= x + 54 && my >= y && my <= y + 19;
        context.fill(x, y, x + 54, y + 19, hovered ? 0x997846A9 : 0x66415268);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + 27, y + 5, TEXT);
    }

    private int laneColor(int lane) {
        return switch (lane) {
            case 0 -> CYAN;
            case 1 -> ACCENT;
            case 2 -> 0xFF74D89B;
            default -> 0xFFF2BA68;
        };
    }

    private String axisForSlider(int index) {
        if ("eyes".equals(selectedBone)) return switch (index) { case 0 -> "x"; case 1 -> "y"; default -> "open"; };
        return switch (index) { case 0 -> "x"; case 1 -> "y"; default -> "z"; };
    }

    private String sliderLabel(int index) {
        if ("eyes".equals(selectedBone)) return switch (index) { case 0 -> "Gaze yaw"; case 1 -> "Gaze pitch"; default -> "Openness"; };
        if ("rotate".equals(mode)) return switch (index) { case 0 -> "Pitch / X"; case 1 -> "Yaw / Y"; default -> "Roll / Z"; };
        if ("move".equals(mode)) return switch (index) { case 0 -> "Position X"; case 1 -> "Position Y"; default -> "Position Z"; };
        return switch (index) { case 0 -> "Scale X"; case 1 -> "Scale Y"; default -> "Scale Z"; };
    }

    private float sliderMin(int index) {
        if ("eyes".equals(selectedBone)) return index == 2 ? 0F : -90F;
        if ("move".equals(mode)) return -1F;
        if ("scale".equals(mode)) return .05F;
        return -180F;
    }

    private float sliderMax(int index) {
        if ("eyes".equals(selectedBone)) return index == 2 ? 1F : 90F;
        if ("move".equals(mode)) return 1F;
        if ("scale".equals(mode)) return 4F;
        return 180F;
    }

    private float sliderValue(int index) {
        StudioFrame frame = CinematicStudio.selectedFrame();
        if (frame == null) return 0F;
        String axis = axisForSlider(index);
        if ("eyes".equals(selectedBone)) {
            return switch (axis) {
                case "x" -> frame.eyes.lookYaw;
                case "y" -> frame.eyes.lookPitch;
                default -> frame.eyes.openness;
            };
        }
        StudioFrame.BonePose pose = frame.bones.get(selectedBone);
        if (pose == null) return mode.equals("scale") ? 1F : 0F;
        if (mode.equals("move")) return switch (axis) { case "x" -> pose.x; case "y" -> pose.y; default -> pose.z; };
        if (mode.equals("scale")) return switch (axis) { case "x" -> pose.scaleX; case "y" -> pose.scaleY; default -> pose.scaleZ; };
        return switch (axis) { case "x" -> pose.pitch; case "y" -> pose.yaw; default -> pose.roll; };
    }

    private void applySlider(int index, double mouseX) {
        float min = sliderMin(index), max = sliderMax(index);
        int barX = rightX + 12, barW = rightW - 24;
        float ratio = (float)Math.max(0D, Math.min(1D, (mouseX - barX) / barW));
        float next = min + ratio * (max - min);
        float delta = next - lastSliderValue;
        if (Math.abs(delta) > 0.0001F) {
            if ("eyes".equals(selectedBone)) {
                String channel = index == 0 ? "eye_yaw" : index == 1 ? "eye_pitch" : "eye_open";
                CinematicStudio.nudgeSelected(channel, delta);
            } else {
                CinematicStudio.editBone(selectedBone, mode, axisForSlider(index), delta);
            }
            lastSliderValue = next;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1 && !insideDock(mouseX, mouseY)) {
            cameraDragging = true;
            return true;
        }
        if (button != 0) return false;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= leftY + 28 && mouseY < leftY + leftH) {
            int index = (int)((mouseY - (leftY + 28)) / 23);
            if (index >= 0 && index < BONE_IDS.length) selectedBone = BONE_IDS[index];
            return true;
        }

        int tabY = rightY + 49;
        if (mouseY >= tabY && mouseY <= tabY + 21) {
            if (mouseX >= rightX + 7 && mouseX <= rightX + 63) mode = "rotate";
            else if (mouseX >= rightX + 66 && mouseX <= rightX + 118) mode = "move";
            else if (mouseX >= rightX + 121 && mouseX <= rightX + 181) mode = "scale";
            return true;
        }

        for (int i = 0; i < 3; i++) {
            int y = rightY + 91 + i * 47;
            if (mouseX >= rightX + 10 && mouseX <= rightX + rightW - 10
                    && mouseY >= y + 13 && mouseY <= y + 33) {
                activeSlider = i;
                lastSliderValue = sliderValue(i);
                applySlider(i, mouseX);
                return true;
            }
        }

        if ("eyes".equals(selectedBone)) {
            int expressionY = rightY + 260;
            if (mouseY >= expressionY && mouseY <= expressionY + 20) {
                if (mouseX < rightX + 64) CinematicStudio.setExpression("normal");
                else if (mouseX < rightX + 122) CinematicStudio.setExpression("blink");
                else if (mouseX < rightX + 184) CinematicStudio.setExpression("happy");
                return true;
            }
        }

        int timelineTop = timelineY + 12;
        if (mouseY >= timelineTop && mouseY <= timelineY + 88
                && mouseX >= trackX && mouseX <= trackX + trackW) {
            int count = CinematicStudio.project().frames.size();
            if (count > 0) {
                float fraction = (float)(mouseX - trackX) / Math.max(1, trackW - 2);
                CinematicStudio.selectFrame(Math.round(fraction * (count - 1)));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 1 && cameraDragging) {
            CinematicStudio.rotateFreeCamera(deltaX, deltaY);
            return true;
        }
        if (button == 0 && activeSlider >= 0) {
            applySlider(activeSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) cameraDragging = false;
        if (button == 0 && activeSlider >= 0) { activeSlider = -1; CinematicStudio.save(); }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isCameraKey(keyCode)) {
            CinematicStudio.cameraKey(keyCode, true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isCameraKey(keyCode)) {
            CinematicStudio.cameraKey(keyCode, false);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private static boolean isCameraKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S
                || key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_SPACE
                || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_LEFT_CONTROL;
    }

    private boolean insideDock(double x, double y) {
        return (x >= leftX && x <= leftX + leftW && y >= leftY && y <= leftY + leftH)
                || (x >= rightX && x <= rightX + rightW && y >= rightY && y <= rightY + rightH)
                || (y >= timelineY && y <= timelineY + 92)
                || (y >= 8 && y <= 35);
    }

    private void panel(DrawContext context, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        context.fill(x, y, x + w, y + h, color);
        context.fill(x, y, x + w, y + 1, BORDER);
        context.fill(x, y + h - 1, x + w, y + h, BORDER);
        context.fill(x, y, x + 1, y + h, BORDER);
        context.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    private String labelForBone(String id) {
        for (int i = 0; i < BONE_IDS.length; i++) if (BONE_IDS[i].equals(id)) return BONE_LABELS[i];
        return id;
    }

    private static String format(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    @Override
    public void removed() {
        CinematicStudio.leaveStudio(MinecraftClient.getInstance());
        super.removed();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
