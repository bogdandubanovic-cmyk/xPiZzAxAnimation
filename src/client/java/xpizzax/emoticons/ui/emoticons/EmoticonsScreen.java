package xpizzax.emoticons.ui.emoticons;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.example.replaynv.client.ReplayControls;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import xpizzax.emoticons.client.AnimationPreferences;
import xpizzax.emoticons.client.EyeAnimationController;
import xpizzax.emoticons.client.renderer.HeadPreviewScope;
import xpizzax.unified.ui.*;

/** The body and head are the two switches; the pencil opens a shared colour palette. */
public final class EmoticonsScreen extends ForegroundScreen {
    private static boolean headPreview, bodyPreview;
    private static final org.joml.Quaternionf PREVIEW_ROTATION = new org.joml.Quaternionf();
    private static final org.joml.Quaternionf PREVIEW_ENTITY_ROTATION = new org.joml.Quaternionf();
    private static final org.joml.Vector3f PREVIEW_OFFSET = new org.joml.Vector3f();
    private final ReplayControls.ReturnTarget returnTarget;
    private int left,top,panelWidth,panelHeight,cardWidth,cardHeight,drawerLeft;
    private PreviewButton bodyCard,eyeCard;
    private PencilButton pencil;
    private PaletteDrawer drawer;
    private boolean drawerOpen;
    private float drawerProgress;
    private long lastFrame=System.nanoTime();

    public EmoticonsScreen(){this(null);}
    public EmoticonsScreen(Screen parent){super(Text.literal("xPiZzAxAnimation"));returnTarget=new ReplayControls.ReturnTarget(parent);}
    public static boolean isEnabled(){return AnimationPreferences.bodyEnabled();}
    public static boolean isHeadPreview(){return headPreview;}
    public static boolean isMenuPreview(){return headPreview||bodyPreview;}
    public static String getModelFor(AbstractClientPlayerEntity player){
        if(player==null)return null;
        return player.getSkinTextures().model()==SkinTextures.Model.SLIM?"emoticons/alex":"emoticons/steve";
    }
    @Override protected void init(){
        AnimationPreferences.flushThemeColor();
        panelWidth=Math.min(376,width-20);panelHeight=Math.min(260,height-20);
        cardWidth=(panelWidth-52)/2;cardHeight=panelHeight-52;
        bodyCard=addDrawableChild(new PreviewButton(cardWidth,cardHeight,"ANIMATION",AnimationPreferences::bodyEnabled,AnimationPreferences::setBodyEnabled));
        eyeCard=addDrawableChild(new PreviewButton(cardWidth,cardHeight,"EYES",EyeAnimationController::enabled,EyeAnimationController::setEnabled));
        pencil=addSelectableChild(new PencilButton(button->setDrawerOpen(!drawerOpen)));
        drawer=new PaletteDrawer(textRenderer);
        for(var field:drawer.fields())addSelectableChild(field);
        drawer.open(drawerOpen);bodyCard.active=eyeCard.active=!drawerOpen;pencil.opened(drawerOpen);
        layout();
    }
    private void setDrawerOpen(boolean opened){
        drawerOpen=opened;drawer.open(opened);pencil.opened(opened);
        bodyCard.active=eyeCard.active=!opened;
        setFocused(pencil);
    }
    private void layout(){
        boolean beside=width>=panelWidth+PaletteDrawer.WIDTH+40;
        left=(width-panelWidth)/2-(beside?Math.round((PaletteDrawer.WIDTH+12)*drawerProgress/2):0);
        top=Math.max(10,(height-panelHeight)/2);
        bodyCard.setX(left+18);bodyCard.setY(top+36);
        eyeCard.setX(left+34+cardWidth);eyeCard.setY(top+36);
        pencil.setX(left+panelWidth-32);pencil.setY(top+7);
        drawerLeft=beside?left+panelWidth+12:left+panelWidth-PaletteDrawer.WIDTH-8;
        drawer.layout(drawerLeft+Math.round((PaletteDrawer.WIDTH+12)*(1-drawerProgress)),top+8,panelHeight-16);
    }
    @Override protected void renderForeground(DrawContext c,int mx,int my,float delta){
        long now=System.nanoTime();float t=(float)(1-Math.exp(-Math.min(.05,(now-lastFrame)/1e9)*15));lastFrame=now;
        drawerProgress+=((drawerOpen?1:0)-drawerProgress)*t;
        if(Math.abs(drawerProgress-(drawerOpen?1:0))<.002F)drawerProgress=drawerOpen?1:0;
        layout();
        UiTheme.backdrop(c,width,height);UiTheme.panel(c,left,top,panelWidth,panelHeight);
        renderWidgets(c,mx,my,delta);
        var player=MinecraftClient.getInstance().player;
        if(player!=null){
            int bx=bodyCard.getX()+cardWidth/2,by=bodyCard.getY()+30+(cardHeight-42)/2;
            int hx=eyeCard.getX()+cardWidth/2,hy=eyeCard.getY()+30+(cardHeight-42)/2;
            int lookX=drawerOpen?bx:mx,lookY=drawerOpen?by:my;
            c.enableScissor(bodyCard.getX()+3,bodyCard.getY()+26,bodyCard.getX()+cardWidth-3,bodyCard.getY()+cardHeight-9);
            bodyPreview=true;
            try{renderPreview(c,player,bx,by,Math.min(74,(cardHeight-40)*5/9),.9F,lookX,lookY,false);}
            finally{bodyPreview=false;c.disableScissor();}
            c.enableScissor(eyeCard.getX()+3,eyeCard.getY()+26,eyeCard.getX()+cardWidth-3,eyeCard.getY()+cardHeight-9);
            headPreview=true;
            try{renderPreview(c,player,hx,hy,Math.min(178,(cardWidth-24)*2),1.65F,drawerOpen?hx:mx,drawerOpen?hy:my,true);}
            finally{headPreview=false;c.disableScissor();}
        }
        // Preview entities write depth. Clear it so the drawer stays above the head.
        c.draw();RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,MinecraftClient.IS_SYSTEM_MAC);
        c.getMatrices().push();c.getMatrices().translate(0,0,200);
        if(drawerProgress>0){
            UiTheme.roundGradient(c,left+12,top+32,panelWidth-24,panelHeight-40,UiTheme.alpha(0,Math.round(80*drawerProgress)),UiTheme.alpha(0,Math.round(120*drawerProgress)));
            c.enableScissor(drawerLeft-4,top+4,drawerLeft+PaletteDrawer.WIDTH+4,top+panelHeight-4);
            try{drawer.render(c,mx,my,delta,drawerProgress);}
            finally{c.disableScissor();}
        }
        pencil.render(c,mx,my,delta);
        c.getMatrices().pop();
    }
    private static void renderPreview(DrawContext c,AbstractClientPlayerEntity player,int x,int y,int scale,float centre,int mx,int my,boolean head){
        float yaw=(float)Math.atan((mx-x)/70F)*.45F,pitch=(float)Math.atan((y-my)/70F)*(head?.28F:.16F);
        var rotation=PREVIEW_ROTATION.identity().rotateZ((float)Math.PI).rotateY(yaw).rotateX(pitch);
        var offset=PREVIEW_OFFSET.set(0,-centre,0).rotate(rotation);
        var entityRotation=PREVIEW_ENTITY_ROTATION.identity().rotateY(yaw).rotateX(pitch);
        float body=player.bodyYaw,previousBody=player.prevBodyYaw,headYaw=player.headYaw,previous=player.prevHeadYaw,oldYaw=player.getYaw(),oldPitch=player.getPitch();
        try{
            player.bodyYaw=180;player.prevBodyYaw=180;player.headYaw=180;player.prevHeadYaw=180;player.setYaw(180);player.setPitch(0);
            InventoryScreen.drawEntity(c,x,y,scale,offset,rotation,entityRotation,player);
        }finally{
            if(head)HeadPreviewScope.restore();
            player.bodyYaw=body;player.prevBodyYaw=previousBody;player.headYaw=headYaw;player.prevHeadYaw=previous;player.setYaw(oldYaw);player.setPitch(oldPitch);
        }
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(drawerOpen&&!pencil.isMouseOver(mx,my)){
            if(drawer.click(mx,my,button)){setFocused(null);return true;}
            if(drawer.contains(mx,my)){super.mouseClicked(mx,my,button);return true;}
            setDrawerOpen(false);return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(drawerOpen&&button==0&&drawer.drag(mx,my))return true;
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        boolean used=drawer.release();return super.mouseReleased(mx,my,button)||used;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(drawerOpen&&key==GLFW.GLFW_KEY_ESCAPE){setDrawerOpen(false);return true;}
        if(drawerOpen&&drawer.inputFocused()&&(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)){
            drawer.commit();setFocused(pencil);return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void removed(){AnimationPreferences.flushThemeColor();super.removed();}
    @Override public void close(){AnimationPreferences.flushThemeColor();if(client!=null)returnTarget.restore(client);}

    private static final class PreviewButton extends RpmButton {
        private final BooleanSupplier state;
        private final String label;
        private float level,hover;
        private long last=System.nanoTime();
        PreviewButton(int width,int height,String label,BooleanSupplier state,Consumer<Boolean> set){
            super(0,0,width,height,message(label,state),button->{set.accept(!state.getAsBoolean());button.setMessage(message(label,state));});
            this.state=state;this.label=label;this.level=state.getAsBoolean()?1:0;
        }
        private static Text message(String label,BooleanSupplier state){return Text.literal(label+": ").append(Text.translatable(state.getAsBoolean()?"rpmmodels.ui.on":"rpmmodels.ui.off"));}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            long now=System.nanoTime();double dt=Math.min(.05,(now-last)/1e9);last=now;
            float target=state.getAsBoolean()?1:0;
            level+=(target-level)*(float)(1-Math.exp(-dt*13));
            if(Math.abs(level-target)<.002F)level=target;
            hover+=((interactionHighlighted()?1:0)-hover)*(float)(1-Math.exp(-dt*15));
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            int border=UiTheme.mix(UiTheme.neutralWave(.3F),UiTheme.accent(),level);
            UiTheme.roundGradient(c,x-1,y-1,w+2,h+2,UiTheme.alpha(border,Math.round(18+hover*55)),0x05000000);
            UiTheme.roundGradient(c,x,y,w,h,UiTheme.mix(UiTheme.mix(0xFF303A44,UiTheme.deep(),level),border,hover*.5F),UiTheme.mix(0xFF19212A,UiTheme.surface(.25F),level));
            UiTheme.roundGradient(c,x+2,y+2,w-4,h-4,UiTheme.mix(0xFF141A20,UiTheme.surface(.035F),level),UiTheme.mix(0xFF20272F,UiTheme.surface(.13F),level));
            c.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,label,x+w/2,y+10,UiTheme.mix(0xFF89939C,UiTheme.light(),level));
            UiTheme.horizontalGradient(c,x+34,y+h-5,w-68,1,UiTheme.mix(UiTheme.neutralWave(0),UiTheme.wave(0),level),UiTheme.mix(UiTheme.neutralWave(1),UiTheme.wave(1),level));
        }
    }
}
