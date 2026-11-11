package xpizzax.emoticons.studio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xpizzax.unified.ui.ForegroundScreen;

import java.util.Locale;

/**
 * Responsive, unified cinematic editor.
 * The editor uses the game world as its viewport and keeps all controls inside
 * three clearly separated areas: tools, take/timeline and export.
 */
public final class CinematicStudioScreen extends ForegroundScreen {
    private static final String[] BONES = {
            "body", "low_body", "head", "left_arm", "low_left_arm",
            "right_arm", "low_right_arm", "left_leg", "low_left_leg",
            "right_leg", "low_leg_right"
    };
    private static final int BG = 0xF20B101B;
    private static final int PANEL = 0xE51A2030;
    private static final int PANEL_ALT = 0xE91F2638;
    private static final int BORDER = 0xFF414B64;
    private static final int ACCENT = 0xFFB56BFF;
    private static final int CYAN = 0xFF63D9F2;
    private static final int MUTED = 0xFF9DAAC0;

    private final Screen parent;
    private int left, top, panelW, panelH;
    private int sideW, rightW, centerX, centerW, timelineY;
    private int selectedBone;

    public CinematicStudioScreen(Screen parent) {
        super(Text.literal("xPiZzAxAnimation — Cinematic Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        left = 18;
        top = 16;
        panelW = Math.max(320, width - 36);
        panelH = Math.max(260, height - 32);

        // Layout adapts to Minecraft GUI scale and window size.
        sideW = clamp(width / 8, 112, 166);
        rightW = clamp(width / 5, 178, 248);
        int gap = 10;
        centerX = left + 12 + sideW + gap;
        centerW = Math.max(150, panelW - sideW - rightW - 46 - gap * 2);
        int rightX = centerX + centerW + gap;
        int contentTop = top + 58;
        int contentBottom = top + panelH - 126;
        int contentH = Math.max(130, contentBottom - contentTop);
        timelineY = top + panelH - 112;

        // Header actions.
        int headerY = top + 15;
        int closeW = 28;
        addButton("×", left + panelW - closeW - 12, headerY, closeW, 20, b -> close());
        int actionRight = left + panelW - closeW - 20;
        int actionW = clamp(width / 9, 86, 128);
        addButton("Save project", actionRight - actionW, headerY, actionW, 20,
                b -> { CinematicStudio.save(); clearAndInit(); });
        addButton("Save as…", actionRight - actionW * 2 - 5, headerY, actionW, 20,
                b -> { CinematicStudio.saveProjectAs(); clearAndInit(); });
        addButton("Open project", actionRight - actionW * 3 - 10, headerY, actionW, 20,
                b -> { CinematicStudio.openProject(); clearAndInit(); });
        addButton(CinematicStudio.isRecording() ? "Stop take" : "● Record",
                actionRight - actionW * 4 - 15, headerY, actionW, 20,
                b -> { CinematicStudio.toggleRecording(MinecraftClient.getInstance()); clearAndInit(); });

        // Left tool rail.
        int lx = left + 12;
        int ly = contentTop + 8;
        int innerW = sideW - 16;
        section(lx, ly, "RECORDING");
        ly += 18;
        addButton(CinematicStudio.isRecording() ? "Stop recording" : "Start recording",
                lx, ly, innerW, 21, b -> { CinematicStudio.toggleRecording(MinecraftClient.getInstance()); clearAndInit(); });
        ly += 32;
        section(lx, ly, "CAMERA");
        ly += 18;
        addButton("Add keyframe", lx, ly, innerW, 20, b -> { CinematicStudio.addPoseKeyframe(); clearAndInit(); });
        ly += 24;
        addButton("Camera path " + (CinematicStudio.isCameraPreview() ? "ON" : "OFF"),
                lx, ly, innerW, 20, b -> { CinematicStudio.toggleCameraPreview(); clearAndInit(); });
        ly += 32;
        section(lx, ly, "ANIMATION");
        ly += 18;
        addButton("Bone: " + shortBone(BONES[selectedBone]), lx, ly, innerW, 20,
                b -> { selectedBone = (selectedBone + 1) % BONES.length; clearAndInit(); });
        ly += 24;
        addButton("Live preview " + (CinematicStudio.isLivePreview() ? "ON" : "OFF"),
                lx, ly, innerW, 20, b -> { CinematicStudio.toggleLivePreview(); clearAndInit(); });
        ly += 32;
        section(lx, ly, "PROJECT");
        ly += 18;
        addButton("Load last", lx, ly, innerW, 20, b -> { CinematicStudio.loadLastProject(); clearAndInit(); });
        ly += 24;
        addButton("Delete frame", lx, ly, innerW, 20, b -> { CinematicStudio.deleteSelectedFrame(); clearAndInit(); });

        // Centre inspector: compact, non-overlapping grouped controls.
        int cy = contentTop + 8;
        section(centerX + 8, cy, "CAMERA  /  BODY  /  EYES");
        cy += 24;
        int colGap = 8;
        int colW = Math.max(100, (centerW - 24) / 3);
        addNudgePair(centerX + 8, cy, colW, "Body yaw", "body_yaw", 5F);
        addNudgePair(centerX + 16 + colW, cy, colW, "Head yaw", "head_yaw", 5F);
        addNudgePair(centerX + 24 + colW * 2, cy, colW, "Head pitch", "head_pitch", 5F);
        cy += 44;
        addNudgePair(centerX + 8, cy, colW, "Camera yaw", "camera_yaw", 5F);
        addNudgePair(centerX + 16 + colW, cy, colW, "Camera pitch", "camera_pitch", 5F);
        addNudgePair(centerX + 24 + colW * 2, cy, colW, "Camera FOV", "camera_fov", 2F);
        cy += 44;
        addNudgePair(centerX + 8, cy, colW, "Eye yaw", "eye_yaw", 3F);
        addNudgePair(centerX + 16 + colW, cy, colW, "Eye pitch", "eye_pitch", 3F);
        addNudgePair(centerX + 24 + colW * 2, cy, colW, "Eye openness", "eye_open", .1F);
        cy += 44;
        section(centerX + 8, cy, "EYE EXPRESSION");
        cy += 20;
        int expressionW = Math.min(86, Math.max(54, (centerW - 36) / 3));
        addButton("Normal", centerX + 8, cy, expressionW, 20,
                b -> { CinematicStudio.setExpression("normal"); clearAndInit(); });
        addButton("Blink", centerX + 16 + expressionW, cy, expressionW, 20,
                b -> { CinematicStudio.setExpression("blink"); clearAndInit(); });
        addButton("Happy", centerX + 24 + expressionW * 2, cy, expressionW, 20,
                b -> { CinematicStudio.setExpression("happy"); clearAndInit(); });
        cy += 34;
        section(centerX + 8, cy, "BONE ROTATION");
        cy += 20;
        int boneCol = Math.max(84, (centerW - 34) / 3);
        addBonePair(centerX + 8, cy, boneCol, "Pitch", "pitch");
        addBonePair(centerX + 16 + boneCol, cy, boneCol, "Yaw", "yaw");
        addBonePair(centerX + 24 + boneCol * 2, cy, boneCol, "Roll", "roll");
        cy += 42;
        section(centerX + 8, cy, "CAMERA POSITION");
        cy += 20;
        addNudgePair(centerX + 8, cy, colW, "Camera X", "camera_x", .25F);
        addNudgePair(centerX + 16 + colW, cy, colW, "Camera Y", "camera_y", .25F);
        addNudgePair(centerX + 24 + colW * 2, cy, colW, "Camera Z", "camera_z", .25F);

        // Right export card.
        int rx = rightX + 8;
        int rw = rightW - 16;
        int ry = contentTop + 8;
        section(rx, ry, "VIDEO EXPORT");
        ry += 24;
        StudioExportSettings settings = StudioExportSettings.get();
        addButton("FPS  ·  " + settings.fps, rx, ry, rw, 22,
                b -> { StudioExportSettings.cycleFps(); clearAndInit(); });
        ry += 28;
        addButton("Resolution  ·  " + settings.width + "×" + settings.height, rx, ry, rw, 22,
                b -> { StudioExportSettings.cycleResolution(); clearAndInit(); });
        ry += 28;
        addButton("Quality  ·  " + settings.quality, rx, ry, rw, 22,
                b -> { StudioExportSettings.cycleQuality(); clearAndInit(); });
        ry += 28;
        addButton("Choose output folder…", rx, ry, rw, 22,
                b -> { StudioExportSettings.chooseOutputDirectory(); clearAndInit(); });
        ry += 38;
        addButton(CinematicStudio.isPreviewPlaying() ? "Pause preview" : "▶  Preview take",
                rx, ry, rw, 24, b -> { CinematicStudio.togglePreviewPlayback(); clearAndInit(); });
        ry += 34;
        addButton("Add pose keyframe", rx, ry, rw, 22,
                b -> { CinematicStudio.addPoseKeyframe(); clearAndInit(); });

        // Timeline controls are kept in their own footer row.
        int footerY = timelineY + 67;
        int footerX = centerX + 8;
        addButton("◀", footerX, footerY, 28, 20, b -> {
            CinematicStudio.selectFrame(CinematicStudio.selectedFrameIndex() - 1); clearAndInit();
        });
        addButton("▶", footerX + 32, footerY, 28, 20,
                b -> { CinematicStudio.togglePreviewPlayback(); clearAndInit(); });
        addButton("▶|", footerX + 64, footerY, 34, 20, b -> {
            CinematicStudio.selectFrame(CinematicStudio.selectedFrameIndex() + 1); clearAndInit();
        });
    }

    private void addButton(String label, int x, int y, int w, int h, ButtonWidget.PressAction action) {
        if (w < 24 || x < 0 || x + w > width || y < 0 || y + h > height) return;
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, w, h).build());
    }

    private void addNudgePair(int x, int y, int groupW, String label, String channel, float step) {
        int buttonW = Math.max(24, Math.min(58, groupW / 2));
        int plusW = 24;
        drawLabelLater(label, x, y - 11);
        addButton("−", x, y + 2, buttonW, 20,
                b -> { CinematicStudio.nudgeSelected(channel, -step); clearAndInit(); });
        addButton("+", x + buttonW + 3, y + 2, plusW, 20,
                b -> { CinematicStudio.nudgeSelected(channel, step); clearAndInit(); });
    }

    private void addBonePair(int x, int y, int groupW, String label, String axis) {
        int buttonW = Math.max(24, Math.min(54, groupW / 2));
        drawLabelLater(label + " · " + shortBone(BONES[selectedBone]), x, y - 11);
        addButton("−", x, y + 2, buttonW, 20,
                b -> { CinematicStudio.nudgeBone(BONES[selectedBone], axis, -5F); clearAndInit(); });
        addButton("+", x + buttonW + 3, y + 2, 24, 20,
                b -> { CinematicStudio.nudgeBone(BONES[selectedBone], axis, 5F); clearAndInit(); });
    }

    // Text is painted in renderForeground after the panel backgrounds.
    private final java.util.List<Label> labels = new java.util.ArrayList<>();
    private void drawLabelLater(String value, int x, int y) { labels.add(new Label(value, x, y)); }
    private record Label(String value, int x, int y) {}

    private void section(int x, int y, String title) { drawLabelLater(title, x, y); }

    private static String shortBone(String bone) {
        return bone.replace("low_", "").replace('_', ' ');
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override
    protected void renderForeground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dim the game world, then build the studio surface.
        context.fill(0, 0, width, height, 0x99040A13);
        fillPanel(context, left, top, panelW, panelH, BG);
        fillPanel(context, left + 7, top + 7, panelW - 14, 38, PANEL_ALT);
        fillPanel(context, left + 7, top + 52, sideW, panelH - 174, PANEL);
        fillPanel(context, centerX, top + 52, centerW, panelH - 174, PANEL);
        int rightX = centerX + centerW + 10;
        fillPanel(context, rightX, top + 52, rightW, panelH - 174, PANEL);
        fillPanel(context, left + 7, timelineY - 5, panelW - 14, 94, PANEL_ALT);
        context.fill(left + 10, top + 7, left + panelW - 10, top + 9, ACCENT);

        context.drawTextWithShadow(textRenderer, "xPiZzAxAnimation", left + 18, top + 17, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "CINEMATIC STUDIO  /  F7 OPEN  ·  F8 RECORD",
                left + 18 + textRenderer.getWidth("xPiZzAxAnimation") + 10, top + 17, MUTED);
        context.drawTextWithShadow(textRenderer, "F7/F8 can be rebound in Options → Controls",
                left + 18, top + 34, MUTED);

        for (Label label : labels) {
            context.drawTextWithShadow(textRenderer, label.value(), label.x(), label.y(), label.value().equals(label.value().toUpperCase(Locale.ROOT)) ? CYAN : 0xFFE8ECF7);
        }
        labels.clear();

        StudioProject project = CinematicStudio.project();
        context.drawTextWithShadow(textRenderer, "TAKE TIMELINE",
                left + 18, timelineY + 3, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, project.frames.size() + " samples  ·  " + project.durationTicks + " ticks",
                left + 18, timelineY + 17, MUTED);
        int trackX = left + 150;
        int trackW = panelW - 176;
        int trackY = timelineY + 14;
        context.fill(trackX, trackY, trackX + trackW, trackY + 3, BORDER);
        context.drawTextWithShadow(textRenderer, "CAMERA", left + 18, trackY + 12, CYAN);
        context.drawTextWithShadow(textRenderer, "BODY", left + 18, trackY + 27, ACCENT);
        context.drawTextWithShadow(textRenderer, "EYES", left + 18, trackY + 42, 0xFF79D68C);
        if (!project.frames.isEmpty()) {
            int count = project.frames.size();
            int stride = Math.max(1, (count + 179) / 180);
            for (int i = 0; i < count; i += stride) {
                int px = trackX + (int)((long)i * trackW / Math.max(1, count - 1));
                StudioFrame frame = project.frames.get(i);
                int color = i == CinematicStudio.selectedFrameIndex() ? 0xFFFFD36A
                        : frame.keyframe ? CYAN : 0xFF79D68C;
                context.fill(px, trackY - 3, px + 2, trackY + 52, color);
            }
            StudioFrame selected = CinematicStudio.selectedFrame();
            if (selected != null) {
                context.drawTextWithShadow(textRenderer, "Selected frame " + CinematicStudio.selectedFrameIndex()
                                + "  ·  tick " + selected.tick + (selected.keyframe ? "  ·  KEYFRAME" : ""),
                        left + 150, timelineY + 70, MUTED);
            }
        } else {
            context.drawTextWithShadow(textRenderer, "Start recording in-game to create a take.",
                    trackX, trackY + 17, MUTED);
        }

        StudioExportSettings export = StudioExportSettings.get();
        context.drawTextWithShadow(textRenderer, "OUTPUT",
                rightX + 10, top + panelH - 151, CYAN);
        String output = export.outputDirectory.toString();
        context.drawTextWithShadow(textRenderer, trim(output, rightW - 24),
                rightX + 10, top + panelH - 137, MUTED);
        context.drawTextWithShadow(textRenderer, "Export profile: " + export.fps + " FPS · "
                + export.width + "×" + export.height + " · " + export.quality,
                rightX + 10, top + panelH - 123, MUTED);
        context.drawTextWithShadow(textRenderer, "MP4 rendering is not connected yet.",
                rightX + 10, top + panelH - 109, 0xFFFFC96B);
        context.drawTextWithShadow(textRenderer, "ReplayMod remains independent.",
                left + 18, top + panelH - 15, MUTED);

        renderWidgets(context, mouseX, mouseY, delta);
    }

    private static String trim(String value, int maxWidth) {
        return value.length() > 28 ? "…" + value.substring(Math.max(0, value.length() - 27)) : value;
    }

    private static void fillPanel(DrawContext context, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        context.fill(x, y, x + w, y + h, color);
        context.fill(x, y, x + w, y + 1, BORDER);
        context.fill(x, y + h - 1, x + w, y + h, BORDER);
        context.fill(x, y, x + 1, y + h, BORDER);
        context.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int trackY = timelineY + 9;
        if (button == 0 && mouseY >= trackY && mouseY <= trackY + 58
                && mouseX >= left + 150 && mouseX <= left + panelW - 20) {
            int count = CinematicStudio.project().frames.size();
            if (count > 0) {
                int trackW = panelW - 176;
                float fraction = (float)(mouseX - (left + 150)) / Math.max(1, trackW);
                CinematicStudio.selectFrame(Math.round(fraction * (count - 1)));
                clearAndInit();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
