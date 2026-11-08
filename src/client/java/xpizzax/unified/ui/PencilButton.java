package xpizzax.unified.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

/** Drawn as geometry so it stays crisp at every GUI scale. */
public final class PencilButton extends RpmButton {
    private boolean opened;
    private float hover;
    private long last=System.nanoTime();
    public PencilButton(PressAction action){
        super(0,0,24,24,Text.translatable("rpmmodels.ui.personalization"),action);
        setTooltip(Tooltip.of(Text.translatable("rpmmodels.ui.personalization")));
    }
    public void opened(boolean value){opened=value;}
    @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
        long now=System.nanoTime();float t=(float)(1-Math.exp(-Math.min(.05,(now-last)/1e9)*16));last=now;
        hover+=((interactionHighlighted()||opened?1:0)-hover)*t;
        int x=getX(),y=getY(),outer=UiTheme.mix(UiTheme.surface(.22F),UiTheme.wave(.2F),.4F+hover*.6F);
        UiTheme.roundGradient(c,x-1,y-1,26,26,UiTheme.alpha(outer,40+Math.round(hover*45)),0x05000000);
        UiTheme.roundGradient(c,x,y,24,24,outer,UiTheme.wave(.9F));
        UiTheme.roundGradient(c,x+1,y+1,22,22,UiTheme.surface(.06F),UiTheme.surface(.14F));
        c.getMatrices().push();c.getMatrices().translate(x+12,y+12,0);
        float tilt=-42F-hover*5F+(float)Math.sin(now*1e-9*2)*1.2F;
        c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
        UiTheme.roundGradient(c,-2,-6,4,10,UiTheme.light(),UiTheme.accent());
        c.fill(-2,-7,2,-5,0xFFF3F7FF);c.fill(-1,4,1,7,UiTheme.TEXT);c.fill(0,7,1,8,UiTheme.TEXT);
        c.getMatrices().pop();
    }
}
