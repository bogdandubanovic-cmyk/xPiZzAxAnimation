package xpizzax.unified.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import com.mojang.blaze3d.systems.RenderSystem;
import xpizzax.emoticons.client.AnimationPreferences;

public final class UiTheme {
    private static int displayed = 0xFF000000 | AnimationPreferences.themeColor();
    private static float red=(displayed>>>16)&255,green=(displayed>>>8)&255,blue=displayed&255;
    private static long lastFrame=System.nanoTime();
    private static float phase=phaseNow();
    private static int wave0,wave02,wave09,wave1;
    private static int neutral0,neutral03,neutral1;
    public static final int TEXT = 0xFFF0F8FF;
    public static final int MUTED = 0xFF98AFC7;
    public static final int BG = 0xFF081321;
    public static final int CARD = 0xFF10283E;
    public static final int HOVER = 0xFF1C4D72;
    public static final net.minecraft.util.Identifier LOGO=new net.minecraft.util.Identifier("rpmmodels","textures/gui/logo.png");

    private UiTheme() {}

    static { updateAnimatedColors(); }

    public static int accent() { return displayed; }
    public static int light() { return mix(displayed,0xFFF2FBFF,.32F); }
    public static int deep() { return mix(0xFF09101B,displayed,.55F); }
    public static int surface(float tint) { return mix(0xFF10141E,displayed,tint); }
    public static int alpha(int color,int alpha) { return color&0xFFFFFF | Math.max(0,Math.min(255,alpha))<<24; }
    public static int wave(float position) {
        if(position==0F)return wave0;
        if(position==.2F)return wave02;
        if(position==.9F)return wave09;
        if(position==1F)return wave1;
        return brandColor(position,brandPhase());
    }
    public static int neutralWave(float position) {
        if(position==0F)return neutral0;
        if(position==.3F)return neutral03;
        if(position==1F)return neutral1;
        float pulse=.5F+.5F*(float)Math.sin(brandPhase()-position*4.2F);
        return mix(0xFF414B57,0xFF85919D,.25F+pulse*.5F);
    }
    private static void advance() {
        long now=System.nanoTime();float amount=(float)(1-Math.exp(-Math.min(.05,(now-lastFrame)/1e9)*18));lastFrame=now;
        phase=(float)((now*8e-10)%(Math.PI*2));
        int target=AnimationPreferences.themeColor();
        red+=(((target>>>16)&255)-red)*amount;green+=(((target>>>8)&255)-green)*amount;blue+=((target&255)-blue)*amount;
        displayed=0xFF000000 | Math.round(red)<<16 | Math.round(green)<<8 | Math.round(blue);
        updateAnimatedColors();
    }
    // These are the only gradient positions used by the animated settings UI.
    // Compute their sine/mix values once per backdrop frame, not for each widget.
    private static void updateAnimatedColors() {
        float current=brandPhase();
        wave0=brandColor(0F,current);
        wave02=brandColor(.2F,current);
        wave09=brandColor(.9F,current);
        wave1=brandColor(1F,current);
        neutral0=neutralColor(0F,current);
        neutral03=neutralColor(.3F,current);
        neutral1=neutralColor(1F,current);
    }
    private static int neutralColor(float position,float currentPhase) {
        float pulse=.5F+.5F*(float)Math.sin(currentPhase-position*4.2F);
        return mix(0xFF414B57,0xFF85919D,.25F+pulse*.5F);
    }

    public static void backdrop(DrawContext context, int width, int height) {
        advance();
        context.fillGradient(0, 0, width, height, alpha(surface(.025F),165),alpha(surface(.10F),205));
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        roundGradient(context,x-2,y-2,width+4,height+4,alpha(light(),90),alpha(deep(),65));
        roundGradient(context,x,y,width,height,surface(.16F),surface(.025F));
        horizontalGradient(context,x+5,y,width-10,1,wave(0),wave(1));
    }
    public static int mix(int a,int b,float t){t=Math.max(0,Math.min(1,t));float q=1-t;return ((int)((a>>>24)*q+(b>>>24)*t)<<24)|((int)(((a>>>16)&255)*q+((b>>>16)&255)*t)<<16)|((int)(((a>>>8)&255)*q+((b>>>8)&255)*t)<<8)|(int)((a&255)*q+(b&255)*t);}
    public static void roundGradient(DrawContext c,int x,int y,int w,int h,int top,int bottom){
        if(w<=0||h<=0)return;
        if(w<8||h<8){c.fillGradient(x,y,x+w,y+h,top,bottom);return;}
        // Disjoint bands: translucent surfaces must not accumulate alpha in the centre.
        int a=mix(top,bottom,2F/h),b=mix(top,bottom,4F/h);
        int d=mix(top,bottom,(h-4F)/h),e=mix(top,bottom,(h-2F)/h);
        c.fillGradient(x+3,y,x+w-3,y+2,top,a);
        c.fillGradient(x+1,y+2,x+w-1,y+4,a,b);
        c.fillGradient(x,y+4,x+w,y+h-4,b,d);
        c.fillGradient(x+1,y+h-4,x+w-1,y+h-2,d,e);
        c.fillGradient(x+3,y+h-2,x+w-3,y+h,e,bottom);
    }
    public static void horizontalGradient(DrawContext c,int x,int y,int w,int h,int left,int right){
        c.getMatrices().push();
        c.getMatrices().translate(x,y+h,0);
        c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.NEGATIVE_Z.rotationDegrees(90));
        c.fillGradient(0,0,h,w,left,right);
        c.getMatrices().pop();
    }
    public static void logo(DrawContext c,int x,int y,int size){
        // One small, batched mesh: the light band moves across the existing logo.
        // No generated textures, timers, resource reloads or work outside this screen.
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);RenderSystem.setShaderTexture(0,LOGO);
        var matrix=c.getMatrices().peek().getPositionMatrix();var buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        float height=size*91F/176,phase=brandPhase();
        for(int i=0;i<12;i++){
            float a=i/12F,b=(i+1)/12F;
            int ca=brandColor(a,phase),cb=brandColor(b,phase);
            float u=(24+176*a)/225F,v=(24+176*b)/225F;
            buffer.vertex(matrix,x+size*a,y+height,0).texture(u,150/225F).color(ca).next();
            buffer.vertex(matrix,x+size*b,y+height,0).texture(v,150/225F).color(cb).next();
            buffer.vertex(matrix,x+size*b,y,0).texture(v,59/225F).color(cb).next();
            buffer.vertex(matrix,x+size*a,y,0).texture(u,59/225F).color(ca).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.setShaderColor(1,1,1,1);
    }
    private static float phaseNow(){return (float)((System.nanoTime()*8e-10)%(Math.PI*2));}
    private static float brandPhase(){return phase;}
    private static int brandColor(float position,float phase){
        float wave=.5F+.5F*(float)Math.sin(phase-position*4.2F);
        return mix(deep(),light(),.35F+wave*.65F);
    }
    public static void brandText(DrawContext c,TextRenderer font,String text,int x,int y){
        float phase=brandPhase();
        int cursor=x;
        for(int i=0;i<text.length();i++){
            String letter=text.substring(i,i+1);
            int tint=mix(brandColor(i/(float)Math.max(1,text.length()-1),phase),0xFFF0F8FF,.45F);
            c.drawText(font,letter,cursor,y,tint,true);cursor+=font.getWidth(letter);
        }
    }
}
