package xpizzax.emoticons.animation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** Pose-only rig: no BBS, meshes, GL buffers, Molang interpreter or resource watchers. */
public final class Rig {
 public static final class Bone {
  public final String name,parentName;
  public Bone parent;
  public final Matrix4f boneMat,relBoneMat=new Matrix4f(),mat=new Matrix4f();
  public final Transform transform=new Transform();
  Bone(String name,String parent,Matrix4f bind){this.name=name;parentName=parent;boneMat=bind;}
 }
 public static final class Transform {
  public final Vector3f translate=new Vector3f(),rotate=new Vector3f(),scale=new Vector3f(1);
  void reset(){translate.zero();rotate.zero();scale.set(1);}
 }
 private record Definition(String name,String parent,Matrix4f bind) {}
 private record Data(List<Definition> bones,Map<String,Clip> clips) {}
 private static final Map<String,Data> DATA=new HashMap<>();
 public final Map<String,Bone> bones=new LinkedHashMap<>();
 public final List<Bone> orderedBones=new ArrayList<>();
 public final Map<String,Clip> clips;
 private float headPitch,headYaw;
 public void setHeadAngles(float yawDegrees,float pitchDegrees){
  // Use the renderer's already-interpolated, body-relative angles for this draw.
  headPitch=Float.isFinite(pitchDegrees)?pitchDegrees*(float)Math.PI/180F:0F;
  headYaw=Float.isFinite(yawDegrees)?-yawDegrees*(float)Math.PI/180F:0F;
 }
 private final Matrix4f scratch=new Matrix4f();
 private final Map<Clip,Channel[][]> boundChannels=new IdentityHashMap<>();
 public Rig(String skin) {
  Data data=DATA.computeIfAbsent(skin,Rig::load);
  clips=data.clips();
  for(Definition def:data.bones()){Bone b=new Bone(def.name(),def.parent(),new Matrix4f(def.bind()));bones.put(b.name,b);orderedBones.add(b);}
  for(Clip clip:clips.values()){
   Channel[][] bound=new Channel[orderedBones.size()][];
   for(int i=0;i<bound.length;i++)bound[i]=clip.parts.get(orderedBones.get(i).name);
   boundChannels.put(clip,bound);
  }
  for(Bone b:orderedBones){b.parent=bones.get(b.parentName);if(b.parent==null)b.relBoneMat.set(b.boneMat);else b.relBoneMat.set(b.parent.boneMat).invert().mul(b.boneMat);}
 }
 private static Data load(String skin) {
  List<Definition> definitions=new ArrayList<>();Map<String,Clip> clips=new HashMap<>();
  read(skin+".rig",(line)->{
   if(!line.startsWith("arm_bone "))return;
   // Empty root parent occupies its own field in the original BOBJ format.
   String[] t=line.split("\\s");float[] matrix=new float[16];for(int i=0;i<16;i++)matrix[i]=Float.parseFloat(t[i+6]);
   definitions.add(new Definition(t[1],t[2],new Matrix4f().set(matrix).transpose()));
  });
  final Clip[] current={null};final Channel[][] group={null};final Channel[] channel={null};
  read("actions.rig",(line)->{
   String[] t=line.split("\\s+");switch(t[0]) {
    case "an": current[0]=new Clip(t[1]);clips.put(t[1],current[0]);break;
    case "ao": group[0]=new Channel[9];current[0].parts.put(t[1],group[0]);break;
    case "ag": int base=t[1].equals("location")?0:t[1].equals("scale")?3:6;channel[0]=new Channel();group[0][base+Integer.parseInt(t[2])]=channel[0];break;
    case "kf": float tick=Float.parseFloat(t[1]);channel[0].keys.add(new Channel.Key(tick,Float.parseFloat(t[2]),t.length>3?t[3]:"LINEAR"));current[0].length=Math.max(current[0].length,tick);break;
   }
  });
  for(Clip clip:clips.values())for(Channel[] channels:clip.parts.values())for(Channel c:channels)if(c!=null){c.keys.sort(Comparator.comparingDouble(Channel.Key::tick));c.compile();}
  return new Data(List.copyOf(definitions),Map.copyOf(clips));
 }
 private static void read(String file,java.util.function.Consumer<String> consumer) {
  try(InputStream stream=Rig.class.getResourceAsStream("/assets/xpizzax_emoticons/"+file)) {
   if(stream==null)throw new IOException("Missing animation resource "+file);
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)consumer.accept(line);}
  }catch(IOException ex){throw new IllegalStateException("Cannot load Emoticons rig",ex);}
 }
 public void resetPose(){for(Bone bone:orderedBones)bone.transform.reset();}
 public void setupMatrices(){for(Bone b:orderedBones){
  b.mat.set(b.relBoneMat).translate(b.transform.translate);
  b.mat.rotateZ(b.transform.rotate.z).rotateY(b.transform.rotate.y).rotateX(b.transform.rotate.x).scale(b.transform.scale);
  if(b.parent!=null){scratch.set(b.parent.mat).mul(b.mat);b.mat.set(scratch);}
 }}
 public void apply(LivingEntity entity,Clip clip,float tick,float blend,float delta,boolean skipInitial){
  Channel[][] bound=boundChannels.get(clip);
  for(int boneIndex=0;boneIndex<orderedBones.size();boneIndex++){
   Bone bone=orderedBones.get(boneIndex);Channel[] part=bound[boneIndex];Transform t=bone.transform;
   if(part==null&&skipInitial&&!bone.name.equals("head"))continue;
   for(int i=0;i<9;i++){
    float initial=i>=3&&i<6?1:0;
    float value=part==null||part[i]==null?initial:part[i].sample(tick,initial);
    if(bone.name.equals("head")&&(part==null||part[6]==null)){
     if(i==6)value=headPitch;
     if(i==7)value=headYaw;
    }
    Vector3f vector=i<3?t.translate:i<6?t.scale:t.rotate;int axis=i%3;float old=axis==0?vector.x:axis==1?vector.y:vector.z;float next=old+(value-old)*blend;
    if(axis==0)vector.x=next;else if(axis==1)vector.y=next;else vector.z=next;
   }
  }
 }
 public void postApply(LivingEntity entity,Clip clip,float tick,float delta){apply(entity,clip,tick,1,delta,true);}
}
