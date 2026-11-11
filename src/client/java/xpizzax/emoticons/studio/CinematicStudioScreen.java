package xpizzax.emoticons.studio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xpizzax.unified.ui.ForegroundScreen;
import xpizzax.unified.ui.UiTheme;

/** One combined timeline/editor surface for camera, body, eyes and export. */
public final class CinematicStudioScreen extends ForegroundScreen {
    private int panelLeft, panelTop, panelWidth;
    private final Screen parent;

    public CinematicStudioScreen(Screen parent) {
        super(Text.literal("xPiZzAxAnimation — Cinematic Studio"));
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(700, width - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(8, (height - 430) / 2);
        int x = panelLeft + 18, y = panelTop + 48;

        addDrawableChild(ButtonWidget.builder(Text.literal("Load last project"), b -> {
            CinematicStudio.loadLastProject(); clearAndInit();
        }).dimensions(x, y, 128, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save project"), b -> CinematicStudio.save())
                .dimensions(x + 134, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Add keyframe"), b -> {
            CinematicStudio.addPoseKeyframe(); clearAndInit();
        }).dimensions(x + 240, y, 105, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(panelLeft + panelWidth - 82, y, 64, 20).build());

        StudioExportSettings settings = StudioExportSettings.get();
        addDrawableChild(ButtonWidget.builder(Text.literal("FPS: " + settings.fps), b -> {
            StudioExportSettings.cycleFps(); clearAndInit();
        }).dimensions(x, y + 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Resolution: " + settings.width + "x" + settings.height), b -> {
            StudioExportSettings.cycleResolution(); clearAndInit();
        }).dimensions(x + 106, y + 28, 190, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Quality: " + settings.quality), b -> {
            StudioExportSettings.cycleQuality(); clearAndInit();
        }).dimensions(x + 302, y + 28, 130, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Choose output folder"), b -> {
            StudioExportSettings.chooseOutputDirectory(); clearAndInit();
        }).dimensions(x + 438, y + 28, 150, 20).build());

        int editY = panelTop + 320;
        addEditButtons(x, editY, "Body yaw", "body_yaw");
        addEditButtons(x + 150, editY, "Head yaw", "head_yaw");
        addEditButtons(x + 300, editY, "Head pitch", "head_pitch");
        addEditButtons(x, editY + 42, "Eye yaw", "eye_yaw");
        addEditButtons(x + 150, editY + 42, "Eye pitch", "eye_pitch");
        addEditButtons(x + 300, editY + 42, "Eye openness", "eye_open");
        addDrawableChild(ButtonWidget.builder(Text.literal("Expression: normal"), b -> {
            CinematicStudio.setExpression("normal"); clearAndInit();
        }).dimensions(x + 450, editY, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Expression: blink"), b -> {
            CinematicStudio.setExpression("blink"); clearAndInit();
        }).dimensions(x + 450, editY + 24, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Expression: happy"), b -> {
            CinematicStudio.setExpression("happy"); clearAndInit();
        }).dimensions(x + 450, editY + 48, 150, 20).build());
    }

    private void addEditButtons(int x, int y, String label, String channel) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + " −"), b -> {
            CinematicStudio.nudgeSelected(channel, channel.equals("eye_open") ? -.1F : -5F); clearAndInit();
        }).dimensions(x, y, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            CinematicStudio.nudgeSelected(channel, channel.equals("eye_open") ? .1F : 5F); clearAndInit();
        }).dimensions(x + 74, y, 30, 20).build());
    }

    @Override protected void renderForeground(DrawContext context, int mouseX, int mouseY, float delta) {
        UiTheme.backdrop(context, width, height);
        UiTheme.panel(context, panelLeft, panelTop, panelWidth, 430);
        context.drawTextWithShadow(textRenderer, "CINEMATIC STUDIO", panelLeft + 18, panelTop + 16, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "F7: open studio  ·  F8: start / stop recording",
                panelLeft + 18, panelTop + 32, CinematicStudio.isRecording() ? 0xFFFF5555 : 0xFFB8C4D0);
        renderWidgets(context, mouseX, mouseY, delta);

        StudioProject project = CinematicStudio.project();
        int timelineY = panelTop + 132;
        context.drawTextWithShadow(textRenderer, "TIMELINE · " + project.frames.size() + " sampled frames",
                panelLeft + 18, timelineY - 18, 0xFFFFFFFF);
        int available = panelWidth - 36;
        context.fill(panelLeft + 18, timelineY, panelLeft + 18 + available, timelineY + 3, 0xFF39434D);
        if (!project.frames.isEmpty()) {
            int selected = CinematicStudio.selectedFrameIndex();
            int count = project.frames.size();
            int step = Math.max(1, (count + 79) / 80);
            for (int i = 0; i < count; i += step) {
                int px = panelLeft + 18 + (int)((long)i * available / Math.max(1, count - 1));
                int color = i == selected ? 0xFFFFC857 : 0xFF73C9A5;
                context.fill(px, timelineY - 5, px + 2, timelineY + 9, color);
            }
            StudioFrame frame = CinematicStudio.selectedFrame();
            if (frame != null) {
                context.drawTextWithShadow(textRenderer, "Frame " + selected + " · tick " + frame.tick
                        + " · body yaw " + Math.round(frame.bodyYaw)
                        + " · head " + Math.round(frame.headYaw) + " / " + Math.round(frame.headPitch)
                        + " · eyes " + frame.eyes.expression,
                        panelLeft + 18, timelineY + 18, 0xFFDCE5EC);
                context.drawTextWithShadow(textRenderer, "Camera: " + String.format("%.1f, %.1f, %.1f",
                        frame.cameraX, frame.cameraY, frame.cameraZ),
                        panelLeft + 18, timelineY + 38, 0xFF9BA9B5);
            }
        }
        StudioExportSettings export = StudioExportSettings.get();
        context.drawTextWithShadow(textRenderer, "Output: " + export.outputDirectory,
                panelLeft + 18, panelTop + 280, 0xFF9BA9B5);
        context.drawTextWithShadow(textRenderer, "Export profile: " + export.fps + " FPS · "
                + export.width + "x" + export.height + " · " + export.quality + " · "
                + export.bitrateMbps + " Mbps",
                panelLeft + 18, panelTop + 296, 0xFF9BA9B5);
        context.drawTextWithShadow(textRenderer,
                "All edits stay in this take: camera path, body pose and eye expression.",
                panelLeft + 18, panelTop + 310, 0xFF9BA9B5);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int timelineY = panelTop + 132;
        if (button == 0 && mouseY >= timelineY - 9 && mouseY <= timelineY + 14
                && mouseX >= panelLeft + 18 && mouseX <= panelLeft + panelWidth - 18) {
            int count = CinematicStudio.project().frames.size();
            if (count > 0) {
                float fraction = (float)(mouseX - (panelLeft + 18)) / (panelWidth - 36);
                CinematicStudio.selectFrame(Math.round(fraction * (count - 1)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
