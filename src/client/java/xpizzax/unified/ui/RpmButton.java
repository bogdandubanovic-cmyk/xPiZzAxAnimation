package xpizzax.unified.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Keyboard-narrated vanilla button with a blue/cyan animated surface. */
public class RpmButton extends ButtonWidget {
    private float hover;private long last=System.nanoTime();private boolean selected;
    public RpmButton(int x,int y,int w,int h,Text text,PressAction action){super(x,y,w,h,text,action,DEFAULT_NARRATION_SUPPLIER);}
    public static Builder builder(Text text,PressAction action){return new Builder(text,action);}
    public static final class Builder extends ButtonWidget.Builder {
        private final Text text;private final PressAction action;private int x,y,w=150,h=20;
        private Builder(Text text,PressAction action){super(text,action);this.text=text;this.action=action;}
        @Override public Builder dimensions(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        @Override public RpmButton build(){return new RpmButton(x,y,w,h,text,action);}
    }
    public void selected(boolean value){selected=value;}
    protected final boolean interactionHighlighted(){
        return active&&(isHovered()||(isFocused()&&MinecraftClient.getInstance().getNavigationType().isKeyboard()));
    }
    @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
        long now=System.nanoTime();double dt=Math.min(.05,(now-last)/1e9);last=now;
        float target=interactionHighlighted()?1:0;hover+=(target-hover)*(float)(1-Math.exp(-dt*15));
        float amount=active?(selected?.68f:.28f)+hover*.3f:0;
        int top=UiTheme.mix(UiTheme.surface(.10F),UiTheme.deep(),amount),bottom=UiTheme.mix(UiTheme.surface(.025F),UiTheme.deep(),amount*.60F);
        UiTheme.roundGradient(c,getX(),getY(),getWidth(),getHeight(),top,bottom);
        c.fill(getX()+3,getY()+getHeight()-1,getX()+getWidth()-3,getY()+getHeight(),active?UiTheme.mix(UiTheme.surface(.2F),UiTheme.accent(),selected?.95f:hover*.65f+.15f):0xFF34414C);
        var font=MinecraftClient.getInstance().textRenderer;String text=font.trimToWidth(getMessage().getString(),Math.max(0,getWidth()-8));
        c.drawCenteredTextWithShadow(font,text,getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?UiTheme.TEXT:UiTheme.MUTED);
    }
}
