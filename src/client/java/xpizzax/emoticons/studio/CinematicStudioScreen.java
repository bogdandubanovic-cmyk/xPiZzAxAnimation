package xpizzax.emoticons.studio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xpizzax.unified.ui.ForegroundScreen;
import xpizzax.unified.ui.UiTheme;

/** Unified in-game editor: take, timeline, camera, body, named bones, eyes and project files. */
public final class CinematicStudioScreen extends ForegroundScreen {
    private static final String[] BONES = {"torso", "left_arm", "right_arm", "left_leg", "right_leg"};
    private int panelLeft, panelTop, panelWidth, panelHeight, selectedBone;
    private final Screen parent;

    public CinematicStudioScreen(Screen parent) {
        super(Text.literal("xPiZzAxAnimation — Cinematic Studio"));
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(920, width - 24);
        panelHeight = Math.min(520, height - 16);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int x = panelLeft + 16, y = panelTop + 42;

        addButton("Open project", x, y, 94, b -> { CinematicStudio.openProject(); clearAndInit(); });
        addButton("Load last", x+98, y, 82, b -> { CinematicStudio.loadLastProject(); clearAndInit(); });
        addButton("Save", x+184, y, 62, b -> CinematicStudio.save());
        addButton("Save as…", x+250, y, 78, b -> CinematicStudio.saveProjectAs());
        addButton("Keyframe", x+332, y, 78, b -> { CinematicStudio.addPoseKeyframe(); clearAndInit(); });
        addButton("Delete frame", x+414, y, 92, b -> { CinematicStudio.deleteSelectedFrame(); clearAndInit(); });
        addButton(CinematicStudio.isRecording() ? "Stop take" : "Record take", x+510, y, 100,
                b -> { CinematicStudio.toggleRecording(MinecraftClient.getInstance()); clearAndInit(); });
        addButton("Close", panelLeft+panelWidth-78, y, 62, b -> close());

        StudioExportSettings settings = StudioExportSettings.get();
        int exportY = y + 27;
        addButton("FPS: "+settings.fps, x, exportY, 92, b -> { StudioExportSettings.cycleFps(); clearAndInit(); });
        addButton("Size: "+settings.width+"×"+settings.height, x+96, exportY, 160,
                b -> { StudioExportSettings.cycleResolution(); clearAndInit(); });
        addButton("Quality: "+settings.quality, x+260, exportY, 130,
                b -> { StudioExportSettings.cycleQuality(); clearAndInit(); });
        addButton("Output folder…", x+394, exportY, 130,
                b -> { StudioExportSettings.chooseOutputDirectory(); clearAndInit(); });

        int controlsY = panelTop + 220;
        addEditButtons(x, controlsY, "Body yaw", "body_yaw", 5F);
        addEditButtons(x+150, controlsY, "Head yaw", "head_yaw", 5F);
        addEditButtons(x+300, controlsY, "Head pitch", "head_pitch", 5F);
        addEditButtons(x+450, controlsY, "Camera yaw", "camera_yaw", 5F);
        addEditButtons(x+600, controlsY, "Camera pitch", "camera_pitch", 5F);

        int eyesY = controlsY + 34;
        addEditButtons(x, eyesY, "Eye yaw", "eye_yaw", 3F);
        addEditButtons(x+150, eyesY, "Eye pitch", "eye_pitch", 3F);
        addEditButtons(x+300, eyesY, "Eye openness", "eye_open", .1F);
        addEditButtons(x+450, eyesY, "Camera FOV", "camera_fov", 2F);
        addButton("Normal eyes", x+600, eyesY, 92, b -> { CinematicStudio.setExpression("normal"); clearAndInit(); });
        addButton("Blink", x+696, eyesY, 64, b -> { CinematicStudio.setExpression("blink"); clearAndInit(); });
        addButton("Happy", x+764, eyesY, 70, b -> { CinematicStudio.setExpression("happy"); clearAndInit(); });

        int boneY = eyesY + 36;
        addButton("Bone: "+BONES[selectedBone], x, boneY, 142, b -> {
            selectedBone = (selectedBone+1)%BONES.length; clearAndInit();
        });
        addBoneButtons(x+150, boneY, "Pitch", "pitch");
        addBoneButtons(x+360, boneY, "Yaw", "yaw");
        addBoneButtons(x+570, boneY, "Roll", "roll");
    }

    private void addButton(String label, int x, int y, int w, java.util.function.Consumer<ButtonWidget> action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, w, 20).build());
    }

    private void addEditButtons(int x, int y, String label, String channel, float step) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label+" −"), b -> {
            CinematicStudio.nudgeSelected(channel, -step); clearAndInit();
        }).dimensions(x, y, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            CinematicStudio.nudgeSelected(channel, step); clearAndInit();
        }).dimensions(x+72, y, 28, 20).build());
    }

    private void addBoneButtons(int x, int y, String label, String axis) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label+" −"), b -> {
            CinematicStudio.nudgeBone(BONES[selectedBone], axis, -5F); clearAndInit();
        }).dimensions(x, y, 66, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            CinematicStudio.nudgeBone(BONES[selectedBone], axis, 5F); clearAndInit();
        }).dimensions(x+70, y, 28, 20).build());
    }

    @Override protected void renderForeground(DrawContext context, int mouseX, int mouseY, float delta) {
        UiTheme.backdrop(context, width, height);
        UiTheme.panel(context, panelLeft, panelTop, panelWidth, panelHeight);
        context.drawTextWithShadow(textRenderer, "xPiZzAxAnimation  /  CINEMATIC STUDIO",
                panelLeft+16, panelTop+13, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,
                "F7 opens this studio by default · change its key in Options → Controls",
                panelLeft+16, panelTop+27, 0xFFB8C4D0);
        renderWidgets(context, mouseX, mouseY, delta);

        StudioProject project = CinematicStudio.project();
        int timelineY = panelTop + 139;
        context.drawTextWithShadow(textRenderer, "TAKE TIMELINE  ·  "+project.frames.size()
                +" samples  ·  "+project.durationTicks+" ticks",
                panelLeft+16, timelineY-18, 0xFFFFFFFF);
        int available = panelWidth-32;
        context.fill(panelLeft+16, timelineY, panelLeft+16+available, timelineY+4, 0xFF39434D);
        if (!project.frames.isEmpty()) {
            int selected = CinematicStudio.selectedFrameIndex();
            int count = project.frames.size();
            int stride = Math.max(1, (count+159)/160);
            for (int i=0;i<count;i+=stride) {
                int px=panelLeft+16+(int)((long)i*available/Math.max(1,count-1));
                StudioFrame f=project.frames.get(i);
                int color=i==selected?0xFFFFC857:(f.keyframe?0xFF63D6FF:0xFF73C9A5);
                context.fill(px,timelineY-5,px+2,timelineY+10,color);
            }
            StudioFrame f=CinematicStudio.selectedFrame();
            if (f!=null) {
                context.drawTextWithShadow(textRenderer,
                        "Selected sample "+selected+"  ·  tick "+f.tick+"  ·  "+(f.keyframe?"KEYFRAME":"sample"),
                        panelLeft+16,timelineY+17,0xFFDCE5EC);
                context.drawTextWithShadow(textRenderer,
                        "Player "+String.format("%.2f, %.2f, %.2f",f.x,f.y,f.z)
                                +"   Camera "+String.format("%.1f, %.1f, %.1f",f.cameraX,f.cameraY,f.cameraZ)
                                +"   View "+Math.round(f.cameraYaw)+"° / "+Math.round(f.cameraPitch)+"°",
                        panelLeft+16,timelineY+32,0xFF9BA9B5);
                StudioFrame interpolated=CinematicStudio.previewAt(f.tick);
                if(interpolated!=null) context.drawTextWithShadow(textRenderer,
                        "Interpolated preview · body "+Math.round(interpolated.bodyYaw)
                                +"° · head "+Math.round(interpolated.headYaw)+"/"+Math.round(interpolated.headPitch)
                                +"° · eyes "+interpolated.eyes.expression+" · openness "+Math.round(interpolated.eyes.openness*100)+"%",
                        panelLeft+16,timelineY+47,0xFF9BA9B5);
            }
        } else {
            context.drawTextWithShadow(textRenderer,"No take yet. Press Record take, play normally, then stop and edit.",
                    panelLeft+16,timelineY+17,0xFF9BA9B5);
        }

        StudioExportSettings export=StudioExportSettings.get();
        context.drawTextWithShadow(textRenderer,"Camera · body · eyes",panelLeft+16,panelTop+204,0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,"Bone track: "+BONES[selectedBone]+"   ·   rotation controls add editable keyframes",
                panelLeft+16,panelTop+305,0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,"Project: "+project.name+"   ·   auto-save: config/xpizzaxanimation/studio/last-project.xpa",
                panelLeft+16,panelTop+365,0xFF9BA9B5);
        context.drawTextWithShadow(textRenderer,"Export profile: "+export.fps+" FPS · "+export.width+"×"+export.height
                +" · "+export.quality+" · "+export.bitrateMbps+" Mbps",
                panelLeft+16,panelTop+382,0xFF9BA9B5);
        context.drawTextWithShadow(textRenderer,"Output: "+export.outputDirectory,
                panelLeft+16,panelTop+399,0xFF9BA9B5);
        context.drawTextWithShadow(textRenderer,
                "Export settings are saved; MP4 frame rendering/encoding is not yet connected.",
                panelLeft+16,panelTop+416,0xFFFFC857);
        context.drawTextWithShadow(textRenderer,
                "ReplayMod is not controlled by this editor. F8 records this mod's own take.",
                panelLeft+16,panelTop+433,0xFF9BA9B5);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int timelineY=panelTop+139;
        if(button==0 && mouseY>=timelineY-9 && mouseY<=timelineY+14
                && mouseX>=panelLeft+16 && mouseX<=panelLeft+panelWidth-16) {
            int count=CinematicStudio.project().frames.size();
            if(count>0) {
                float fraction=(float)(mouseX-(panelLeft+16))/(panelWidth-32);
                CinematicStudio.selectFrame(Math.round(fraction*(count-1)));
                return true;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
