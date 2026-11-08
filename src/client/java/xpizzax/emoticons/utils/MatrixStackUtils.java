package xpizzax.emoticons.utils;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
public final class MatrixStackUtils {
 private MatrixStackUtils(){}
 private static final class Scratch { final Matrix3f normal=new Matrix3f();final Vector3f scale=new Vector3f(); }
 private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
 public static void multiply(MatrixStack stack,Matrix4f matrix){
  Scratch scratch=SCRATCH.get();Matrix3f normal=scratch.normal.set(matrix);Vector3f scale=normal.getScale(scratch.scale);
  scale.set(scale.x==0?0:1/scale.x,scale.y==0?0:1/scale.y,scale.z==0?0:1/scale.z);
  normal.scale(scale);stack.peek().getPositionMatrix().mul(matrix);stack.peek().getNormalMatrix().mul(normal);
 }
}
