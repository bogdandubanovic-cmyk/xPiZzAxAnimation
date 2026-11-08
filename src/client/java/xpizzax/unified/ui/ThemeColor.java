package xpizzax.unified.ui;

import java.util.Locale;
import java.util.OptionalInt;

/** Color math shared by the picker, saved preferences and screen gradients. */
public final class ThemeColor {
    public static final int DEFAULT = 0x54C7F5;
    private ThemeColor() {}
    public static OptionalInt parse(String text) {
        if (text == null) return OptionalInt.empty();
        String value = text.strip();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(value, 16));
    }
    public static String hex(int rgb) { return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF); }
    public static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    public static int hsv(float hue, float saturation, float value) {
        hue = (hue % 1 + 1) % 1; saturation = clamp(saturation); value = clamp(value);
        float h = hue * 6, f = h - (int) h;
        float p = value * (1-saturation), q = value * (1-f*saturation), t = value * (1-(1-f)*saturation);
        float r,g,b;
        switch ((int) h) {
            case 0 -> {r=value;g=t;b=p;} case 1 -> {r=q;g=value;b=p;}
            case 2 -> {r=p;g=value;b=t;} case 3 -> {r=p;g=q;b=value;}
            case 4 -> {r=t;g=p;b=value;} default -> {r=value;g=p;b=q;}
        }
        return Math.round(r*255)<<16 | Math.round(g*255)<<8 | Math.round(b*255);
    }
    public static float[] hsv(int rgb) {
        float r=((rgb>>>16)&255)/255F, g=((rgb>>>8)&255)/255F, b=(rgb&255)/255F;
        float max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b)), d=max-min, h=0;
        if(d>0) {
            if(max==r) h=((g-b)/d)%6; else if(max==g) h=(b-r)/d+2; else h=(r-g)/d+4;
            h=(h/6+1)%1;
        }
        return new float[]{h,max==0?0:d/max,max};
    }
}
