package xpizzax.emoticons.animation;
/** Keep one quiet interpolation tick from repeatedly restarting walk/crouch clips. */
public final class MotionGate {
 private boolean moving;private int quiet;
 public boolean update(double dx,double dz){
  double distance=Math.max(Math.abs(dx),Math.abs(dz));
  if(distance>(moving?0.005:0.01)){moving=true;quiet=0;}
  else if(moving&&++quiet>=2){moving=false;quiet=0;}
  return moving;
 }
 public void reset(){moving=false;quiet=0;}
}
