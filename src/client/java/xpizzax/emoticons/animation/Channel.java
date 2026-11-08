package xpizzax.emoticons.animation;
import java.util.*;
/** Keyframes are compiled once into primitive arrays shared by all entity rigs. */
public final class Channel {
 public record Key(float tick,float value,String interpolation) {}
 final List<Key> keys=new ArrayList<>();
 private float[] ticks=new float[0],values=new float[0];
 private byte[] interpolation=new byte[0];
 public void compile(){
  int n=keys.size();ticks=new float[n];values=new float[n];interpolation=new byte[n];
  for(int i=0;i<n;i++){
   Key k=keys.get(i);ticks[i]=k.tick();values[i]=k.value();
   String mode=keys.get(Math.max(0,i-1)).interpolation();
   interpolation[i]=(byte)(mode.equals("CONSTANT")?1:mode.equals("BEZIER")?2:0);
  }
 }
 public float sample(float tick,float fallback) {
  // Runtime channels are immutable after loading; size check also supports verification fixtures.
  if(ticks.length!=keys.size())compile();
  int n=ticks.length;if(n==0)return fallback;
  if(n==1||tick<ticks[0])return values[0];
  if(tick>=ticks[n-1])return values[n-1];
  int low=0,high=n-1;
  while(low<=high){int mid=(low+high)>>>1;if(ticks[mid]<tick)low=mid+1;else high=mid-1;}
  if(ticks[low]==Math.floor(tick)&&low<n-1)low++;
  int ai=Math.max(0,low-1),bi=low;
  float x=Math.max(0,Math.min(1,(tick-ticks[ai])/(ticks[bi]-ticks[ai])));
  if(interpolation[ai]==1)return values[ai];
  if(interpolation[ai]==2){
   double y0=values[Math.max(0,ai-1)],y1=values[ai],y2=values[bi],y3=values[Math.min(n-1,ai+2)];
   double c0=-.5*y0+1.5*y1-1.5*y2+.5*y3,c1=y0-2.5*y1+2*y2-.5*y3,c2=-.5*y0+.5*y2;
   return(float)(((c0*x+c1)*x+c2)*x+y1);
  }
  return values[ai]+(values[bi]-values[ai])*x;
 }
}
