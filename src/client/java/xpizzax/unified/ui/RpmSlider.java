package xpizzax.unified.ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
public abstract class RpmSlider extends SliderWidget {
    protected RpmSlider(int x,int y,int w,int h,Text message,double value){super(x,y,w,h,message,value);}
    @Override public void renderWidget(DrawContext c,int mx,int my,float delta){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        UiTheme.roundGradient(c,x,y,w,h,active?UiTheme.surface(.16F):0xFF1E2B37,UiTheme.surface(.025F));
        int knob=x+(int)Math.round(value*(w-8));
        c.fillGradient(x+2,y+h-3,knob+4,y+h-2,active?UiTheme.accent():0xFF5C6A79,UiTheme.deep());
        UiTheme.roundGradient(c,knob,y+1,8,h-2,active?UiTheme.light():UiTheme.MUTED,active?UiTheme.deep():0xFF52606E);
        var font=MinecraftClient.getInstance().textRenderer;String message=font.trimToWidth(getMessage().getString(),w-12);
        c.drawCenteredTextWithShadow(font,message,x+w/2,y+(h-8)/2,active?UiTheme.TEXT:UiTheme.MUTED);
    }
}
