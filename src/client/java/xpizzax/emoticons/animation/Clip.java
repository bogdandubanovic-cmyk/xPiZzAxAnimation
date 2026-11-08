package xpizzax.emoticons.animation;
import java.util.*;
public final class Clip {
 public final String id;
 public final Map<String,Channel[]> parts=new HashMap<>();
 float length;
 Clip(String id){this.id=id;}
 public int getLengthInTicks(){return(int)Math.floor((double)(length/20F)*20);}
}
