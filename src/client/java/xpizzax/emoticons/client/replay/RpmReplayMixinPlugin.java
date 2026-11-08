package xpizzax.emoticons.client.replay;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.example.replaynv.ReplayState;
import net.fabricmc.loader.api.FabricLoader;

/** RPM contributes its accessor through another Mixin, not through the vanilla class.
 * Install the replay boundary after all methods have been merged; an optional
 * name-only injector can silently find no eligible vanilla method. No RPM classes
 * are loaded here, and clients without RPM are left alone. */
public final class RpmReplayMixinPlugin implements IMixinConfigPlugin {
    private static final String OWNER="xpizzax/emoticons/client/replay/RpmGenderReplay";
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){
        if(!ReplayState.isInstalled())return;
        for(MethodNode method:node.methods){
            if(!method.name.equals("gender$getBreastModel")||!method.desc.equals("()Ljava/util/Optional;"))continue;
            for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(OWNER)&&call.name.equals("replayOverride"))return;
            int count=0;
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn.getOpcode()==Opcodes.ARETURN){
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,OWNER,"fromLiveModel","(Ljava/util/Optional;Ljava/lang/Object;)Ljava/util/Optional;",false));
                method.instructions.insertBefore(insn,hook);count++;
            }
            // Resolve replay state before calling RPM's module singleton at all.
            // It is deliberately cleared on disconnect and replay-world restarts.
            LabelNode live=new LabelNode();InsnList head=new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD,0));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC,OWNER,"replayOverride","(Ljava/lang/Object;)Ljava/util/Optional;",false));
            head.add(new InsnNode(Opcodes.DUP));head.add(new JumpInsnNode(Opcodes.IFNULL,live));head.add(new InsnNode(Opcodes.ARETURN));
            head.add(live);head.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{"java/util/Optional"}));
            head.add(new InsnNode(Opcodes.POP));method.instructions.insert(head);
            method.maxStack=Math.max(method.maxStack,2);
            System.out.println("[xPiZzAxAnimation] Replay gender accessor connected: "+count+" return paths");
        }
    }
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    private static boolean targetPresent(String target){
        // Resource lookup does not initialise another mod or force its classes
        // through Minecraft's transformer while Mixin is still preparing.
        return RpmReplayMixinPlugin.class.getClassLoader().getResource(target.replace('.','/')+".class")!=null;
    }
    @Override public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.endsWith(".PhotoHeldItemMixin"))return FabricLoader.getInstance().isModLoaded("notenoughanimations")&&targetPresent(target);
        if(mixin.endsWith(".PhotoFirstPersonMixin"))return FabricLoader.getInstance().isModLoaded("firstperson")&&targetPresent(target);
        if(mixin.endsWith(".WildfireVanillaBendMixin"))return targetPresent(target);
        if(mixin.contains(".ReplayWildfire"))return ReplayState.isInstalled()&&targetPresent(target);
        return ReplayState.isInstalled() || !mixin.startsWith("xpizzax.emoticons.mixin.client.Replay");
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
