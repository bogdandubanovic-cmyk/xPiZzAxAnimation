package xpizzax.unified.ui;

import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xpizzax.emoticons.client.AnimationPreferences;

/** Live HSV palette; no background/opacity settings and no per-frame file writes. */
public final class PaletteDrawer {
    public static final int WIDTH=212;
    private static final int[] SWATCHES={ThemeColor.DEFAULT,0x7B61FF,0xD05BDF,0xF16E97,0xF27457,0xF2C75C,0x67D3A2,0x73E2E6};
    private static final int[] HUE_COLORS = new int[7];
    private static final String[] RGB_NAMES = {"R", "G", "B"};
    static {
        for (int i = 0; i < HUE_COLORS.length; i++)
            HUE_COLORS[i] = 0xFF000000 | ThemeColor.hsv(i / 6F, 1F, 1F);
    }
    private final TextRenderer font;
    private final TextFieldWidget hex,red,green,blue;
    private final TextFieldWidget[] rgbFields;
    private final List<TextFieldWidget> allFields;
    private int x,y,height,squareY,squareH,hueY,swatchY;
    private float hue,saturation,value;
    private int pureHueColor;
    private boolean syncing,opened;
    private int drag;

    public PaletteDrawer(TextRenderer font) {
        this.font=font;
        float[] hsv=ThemeColor.hsv(AnimationPreferences.themeColor());hue=hsv[0];saturation=hsv[1];value=hsv[2];
        updateHueColor();
        hex=field("HEX",7);red=field("R",3);green=field("G",3);blue=field("B",3);
        rgbFields=new TextFieldWidget[]{red,green,blue};
        allFields=List.of(hex,red,green,blue);
        hex.setChangedListener(text->{if(syncing)return;ThemeColor.parse(text).ifPresent(rgb->fromFields(rgb,hex));});
        for(TextFieldWidget field:List.of(red,green,blue))field.setChangedListener(text->{
            if(syncing)return;
            try {
                int r=Integer.parseInt(red.getText()),g=Integer.parseInt(green.getText()),b=Integer.parseInt(blue.getText());
                if(r>=0&&r<=255&&g>=0&&g<=255&&b>=0&&b<=255)fromFields(r<<16|g<<8|b,field);
            }catch(NumberFormatException ignored){}
        });
        updateFields(null);
    }
    private TextFieldWidget field(String name,int length) {
        TextFieldWidget result=new TextFieldWidget(font,0,0,50,18,Text.literal(name));
        result.setMaxLength(length);result.setDrawsBackground(false);return result;
    }
    public List<TextFieldWidget> fields(){return allFields;}
    public void open(boolean opened){
        this.opened=opened;
        for(var field:allFields){field.active=opened;if(!opened)field.setFocused(false);}
        if(!opened){drag=0;AnimationPreferences.flushThemeColor();updateFields(null);}
    }
    public void layout(int x,int y,int height){
        this.x=x;this.y=y;this.height=height;
        squareY=y+34;squareH=Math.max(40,height-154);hueY=squareY+squareH+8;swatchY=y+height-16;
        hex.setX(x+18);hex.setY(hueY+36);hex.setWidth(146);
        int width=(WIDTH-36)/3;
        for(int i=0;i<rgbFields.length;i++){var field=rgbFields[i];field.setX(x+18+i*(width+4));field.setY(hueY+74);field.setWidth(width-8);}
    }
    public boolean contains(double mx,double my){return mx>=x&&mx<x+WIDTH&&my>=y&&my<y+height;}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    public boolean click(double mx,double my,int button){
        if(!opened||button!=0)return false;
        AnimationPreferences.flushThemeColor();
        if(inside(mx,my,x+12,squareY,WIDTH-24,squareH)){drag=1;pick(mx,my);return true;}
        if(inside(mx,my,x+12,hueY,WIDTH-24,12)){drag=2;pick(mx,my);return true;}
        for(int i=0;i<SWATCHES.length;i++)if(inside(mx,my,x+12+i*24,swatchY,20,10)){
            setRgb(SWATCHES[i]);AnimationPreferences.flushThemeColor();return true;
        }
        return false;
    }
    public boolean drag(double mx,double my){if(drag==0||!opened)return false;pick(mx,my);return true;}
    public boolean release(){if(drag==0)return false;drag=0;AnimationPreferences.flushThemeColor();return true;}
    public void commit(){AnimationPreferences.flushThemeColor();updateFields(null);}
    private void pick(double mx,double my){
        if(drag==1){saturation=ThemeColor.clamp((float)(mx-x-12)/(WIDTH-24));value=1-ThemeColor.clamp((float)(my-squareY)/squareH);}
        else {hue=ThemeColor.clamp((float)(mx-x-12)/(WIDTH-24));updateHueColor();}
        AnimationPreferences.previewThemeColor(ThemeColor.hsv(hue,saturation,value));updateFields(null);
    }
    private void setRgb(int rgb){float[] hsv=ThemeColor.hsv(rgb);hue=hsv[0];saturation=hsv[1];value=hsv[2];updateHueColor();AnimationPreferences.previewThemeColor(rgb);updateFields(null);}
    private void fromFields(int rgb,TextFieldWidget source){float[] hsv=ThemeColor.hsv(rgb);hue=hsv[0];saturation=hsv[1];value=hsv[2];updateHueColor();AnimationPreferences.previewThemeColor(rgb);updateFields(source);}
    private void updateHueColor(){pureHueColor=0xFF000000|ThemeColor.hsv(hue,1F,1F);}
    private void updateFields(TextFieldWidget except){
        syncing=true;int rgb=AnimationPreferences.themeColor();
        if(except!=hex)hex.setText(ThemeColor.hex(rgb));
        if(except!=red)red.setText(Integer.toString((rgb>>>16)&255));
        if(except!=green)green.setText(Integer.toString((rgb>>>8)&255));
        if(except!=blue)blue.setText(Integer.toString(rgb&255));
        syncing=false;
    }
    public boolean inputFocused(){return hex.isFocused()||red.isFocused()||green.isFocused()||blue.isFocused();}
    public void render(DrawContext c,int mx,int my,float delta,float progress){
        UiTheme.roundGradient(c,x-3,y-3,WIDTH+6,height+6,UiTheme.alpha(UiTheme.accent(),Math.round(45*progress)),0x08000000);
        UiTheme.roundGradient(c,x,y,WIDTH,height,UiTheme.surface(.10F),0xFF0C1018);
        UiTheme.horizontalGradient(c,x+5,y,WIDTH-10,1,UiTheme.wave(0),UiTheme.wave(1));
        c.drawText(font,Text.translatable("rpmmodels.ui.personalization"),x+12,y+12,UiTheme.TEXT,false);
        UiTheme.horizontalGradient(c,x+12,squareY,WIDTH-24,squareH,0xFFFFFFFF,pureHueColor);
        c.fillGradient(x+12,squareY,x+WIDTH-12,squareY+squareH,0x00000000,0xFF000000);
        int sx=x+12+Math.round(saturation*(WIDTH-24)),sy=squareY+Math.round((1-value)*squareH);
        c.drawBorder(sx-3,sy-3,7,7,0xFF11151D);c.drawBorder(sx-2,sy-2,5,5,0xFFFFFFFF);
        for(int i=0;i<6;i++){
            int start=x+12+i*(WIDTH-24)/6,end=x+12+(i+1)*(WIDTH-24)/6;
            UiTheme.horizontalGradient(c,start,hueY,end-start,10,HUE_COLORS[i],HUE_COLORS[i+1]);
        }
        int hx=x+12+Math.round(hue*(WIDTH-24));c.drawBorder(hx-2,hueY-2,5,14,0xFFFFFFFF);
        c.drawText(font,"HEX",x+12,hueY+22,UiTheme.MUTED,false);
        UiTheme.roundGradient(c,x+176,hueY+32,22,20,0xFF000000|AnimationPreferences.themeColor(),0xFF000000|AnimationPreferences.themeColor());
        for(int i=0;i<rgbFields.length;i++)c.drawText(font,RGB_NAMES[i],rgbFields[i].getX()-2,hueY+60,UiTheme.MUTED,false);
        for(var field:allFields){
            int color=field.isFocused()?UiTheme.accent():UiTheme.surface(.30F);
            UiTheme.roundGradient(c,field.getX()-5,field.getY()-4,field.getWidth()+6,20,color,UiTheme.surface(.05F));
            UiTheme.roundGradient(c,field.getX()-4,field.getY()-3,field.getWidth()+4,18,0xFF0C1018,0xFF111723);
            field.setEditableColor(UiTheme.TEXT);field.visible=true;field.render(c,mx,my,delta);
        }
        for(int i=0;i<SWATCHES.length;i++){
            int px=x+12+i*24,color=0xFF000000|SWATCHES[i];
            UiTheme.roundGradient(c,px,swatchY,20,10,color,UiTheme.mix(color,0xFF000000,.25F));
            if(inside(mx,my,px,swatchY,20,10))c.drawBorder(px-1,swatchY-1,22,12,UiTheme.TEXT);
        }
    }
}
