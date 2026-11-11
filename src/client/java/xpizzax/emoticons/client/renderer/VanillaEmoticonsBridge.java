package xpizzax.emoticons.client.renderer;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import xpizzax.emoticons.mixin.client.PlayerEntityModelAccessor;
import xpizzax.emoticons.client.AnimationWeaponHelper;
import net.minecraft.util.Arm;
import xpizzax.emoticons.ui.emoticons.EmoticonsScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import xpizzax.emoticons.animation.PoseRenderer;
public final class VanillaEmoticonsBridge {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Frame>> FREE_FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private VanillaEmoticonsBridge() {}
    private static final ThreadLocal<Integer> PREVIEW = ThreadLocal.withInitial(() -> 0);
    public static boolean isPreview() { return PREVIEW.get() > 0 || EmoticonsScreen.isMenuPreview(); }
    public static void beginPreview() { PREVIEW.set(PREVIEW.get() + 1); }
    public static void endPreview() {
        int depth = PREVIEW.get() - 1;
        if (depth <= 0) PREVIEW.remove(); else PREVIEW.set(depth);
    }
    private static final class Frame {
        LivingEntity player;
        LivingEntityRenderer<?, ?> renderer;
        PoseRenderer animation;
        float tickDelta;
        PoseRenderer.VanillaPoseSession session;
        boolean rooted;
        boolean firstPerson;
        void reset(LivingEntity p, LivingEntityRenderer<?, ?> r, PoseRenderer a, float d) {
            player=p; renderer=r; animation=a; tickDelta=d;session=null;rooted=false;
            firstPerson=FirstPersonCompatibility.isBodyPass(p);
        }
        void release() {
            player=null;renderer=null;animation=null;session=null;tickDelta=0;rooted=false;firstPerson=false;
        }
    }

    public static String desiredModel(AbstractClientPlayerEntity player){return EmoticonsScreen.getModelFor(player);}
    private static final IdentityHashMap<LivingEntity,State> STATES=new IdentityHashMap<>();
    // States are added here only when an entity is actually rendered. This avoids
    // scanning the full (up to 1024-entry) identity cache on every client tick.
    private static final ArrayList<State> ACTIVE_STATES=new ArrayList<>(128);
    private static Object world;
    private static long clock;
    private static final class State {
        final LivingEntity entity;
        final String skin;
        final PoseRenderer animation;
        long lastSeen,lastTick=-1;
        boolean activeListed;
        State(LivingEntity entity,String skin){this.entity=entity;this.skin=skin;animation=new PoseRenderer(skin);}
    }
    public static void tickNpcCache(MinecraftClient client){
        if(world!=client.world||!EmoticonsScreen.isEnabled()){
            STATES.clear();ACTIVE_STATES.clear();world=client.world;
        }
        clock++;
        if(client.world==null||client.isPaused())return;

        // The local player must keep advancing even in first person, where a
        // renderer may intentionally hide the player model altogether.
        if(client.player!=null){
            State local=STATES.get(client.player);
            if(local!=null&&local.lastTick!=clock){local.animation.tick(client.player);local.lastTick=clock;}
        }

        // Keep only recently rendered states in this small active list. This
        // preserves the original two-tick visibility grace period without
        // traversing every cached/off-screen player on every client tick.
        var active=ACTIVE_STATES.iterator();
        while(active.hasNext()){
            State state=active.next();
            LivingEntity entity=state.entity;
            boolean local=entity==client.player;
            if(entity.isRemoved()||entity.getWorld()!=client.world||(!local&&clock-state.lastSeen>2)){
                state.activeListed=false;
                active.remove();
                continue;
            }
            if(state.lastTick!=clock){
                state.animation.tick(entity);
                state.lastTick=clock;
            }
        }

        // Removed/off-screen entity references are released in a small periodic
        // sweep instead of forcing a full-map traversal 20 times per second.
        if(clock%20L==0L){
            var it=STATES.entrySet().iterator();
            while(it.hasNext()){
                var entry=it.next();
                LivingEntity entity=entry.getKey();
                boolean local=entity==client.player;
                if(entity.isRemoved()||entity.getWorld()!=client.world||(!local&&clock-entry.getValue().lastSeen>200))it.remove();
            }
        }
    }
    public static boolean beginEntity(LivingEntity entity,LivingEntityRenderer<?,?> renderer,float delta){
        if(EmoticonsScreen.isHeadPreview()||!EmoticonsScreen.isEnabled()||current(entity)!=null||!(renderer.getModel() instanceof PlayerEntityModel<?> model)
            ||entity.hasVehicle()||entity.isSwimming()||entity.isClimbing()||entity.isFallFlying()||AnimationWeaponHelper.isHoldingWeapon(entity))return false;
        if(entity instanceof AbstractClientPlayerEntity player&&OptionalMods.isEmotecraftPlaying(player))return false;
        String skin=((PlayerEntityModelAccessor)(Object)model).xpizzax$isThinArms()?"alex":"steve";
        Object currentWorld=MinecraftClient.getInstance().world;if(world!=currentWorld){STATES.clear();ACTIVE_STATES.clear();world=currentWorld;}
        State state=STATES.get(entity);
        if(state==null||!skin.equals(state.skin)){
            if(state!=null&&state.activeListed){ACTIVE_STATES.remove(state);state.activeListed=false;}
            if(STATES.size()>=1024){
                LivingEntity oldest=null;long seen=Long.MAX_VALUE;
                for(var entry:STATES.entrySet())if(entry.getValue().lastSeen<seen){oldest=entry.getKey();seen=entry.getValue().lastSeen;}
                if(oldest!=null){State removed=STATES.remove(oldest);if(removed!=null&&removed.activeListed){ACTIVE_STATES.remove(removed);removed.activeListed=false;}}
            }
            state=new State(entity,skin);state.lastSeen=clock;STATES.put(entity,state);
        }
        if(clock-state.lastSeen>2&&state.lastTick!=clock&&!FirstPersonCompatibility.isBodyPass(entity)){state.animation.tick(entity);state.lastTick=clock;}
        state.lastSeen=clock;
        if(!state.activeListed){state.activeListed=true;ACTIVE_STATES.add(state);}
        if(PREVIEW.get()>0)delta=MinecraftClient.getInstance().getTickDelta();
        Frame frame=FREE_FRAMES.get().pollFirst();if(frame==null)frame=new Frame();
        frame.reset(entity,renderer,state.animation,delta);FRAMES.get().push(frame);return true;
    }
    public static boolean begin(AbstractClientPlayerEntity player, PlayerEntityRenderer renderer, float tickDelta) {
        return beginEntity(player, renderer, tickDelta);
    }
    /** Captures the user's own current animation rig without touching ReplayMod. */
    public static void captureStudioPose(LivingEntity entity, xpizzax.emoticons.studio.StudioFrame frame) {
        State state = STATES.get(entity);
        if (state == null && entity instanceof AbstractClientPlayerEntity player) {
            String skin = desiredModel(player);
            state = new State(entity, skin);
            state.lastSeen = clock;
            STATES.put(entity, state);
            if (!state.activeListed) { state.activeListed = true; ACTIVE_STATES.add(state); }
            state.animation.tick(entity);
            state.lastTick = clock;
        }
        if (state != null) state.animation.captureStudioPose(entity, frame);
    }
    private static Frame current(LivingEntity entity) {
        Frame f=FRAMES.get().peek(); return f != null && f.player == entity ? f : null;
    }
    public static void root(LivingEntity entity, MatrixStack stack) {
        Frame f=current(entity);
        if(f != null && f.session != null && !f.rooted) { f.animation.applyVanillaRoot(stack); f.rooted=true; }
    }
    public static void pose(LivingEntity entity, PlayerEntityModel<?> model,float headYaw,float headPitch) {
        Frame f=current(entity);
        if(f == null || f.renderer.getModel() != model || f.session != null) return;
        // setAngles supplies the same head angles that vanilla used for this render.
        // Waiting until here also avoids sampling a second orientation before other
        // renderer hooks have finished setting up the entity (including GUI previews).
        if(!f.animation.prepareVanillaPose(entity,f.tickDelta,headYaw,headPitch,f.firstPerson))return;
        f.session=f.animation.applyVanillaPose(model, f.tickDelta, entity);
    }
    public static boolean isFirstPersonBody(LivingEntity entity) {
        Frame f=current(entity);
        return f!=null&&f.firstPerson;
    }
    public static boolean chest(LivingEntity entity, MatrixStack stack) {
        Frame f=current(entity);
        if(f == null || f.session == null) return false;
        stack.push();
        f.animation.applyVanillaChestBend(stack, ((PlayerEntityModel<?>)f.renderer.getModel()).body);
        return true;
    }
    public static void hand(LivingEntity entity, MatrixStack stack, Arm arm) {
        Frame f=current(entity);
        if (f != null && f.session != null) f.animation.applyVanillaHandBend(stack, arm);
    }
    public static boolean hasPose(LivingEntity entity) {
        Frame f=current(entity);
        return f != null && f.session != null;
    }
    public static boolean bindHand(LivingEntity entity, MatrixStack stack, Arm arm) {
        Frame f=current(entity);
        if (f == null || f.session == null) return false;
        // Use the exact model receiving our pose, rather than an optional
        // feature renderer's separate vanilla context model. This also preserves
        // PlayerEntityModel's slim-arm correction and the item-use pose.
        ((PlayerEntityModel<?>)f.renderer.getModel()).setArmAngle(arm, stack);
        f.animation.applyVanillaHandBend(stack, arm);
        return true;
    }
    public static boolean upper(LivingEntity entity, MatrixStack stack) {
        Frame f=current(entity);
        if(f == null || f.session == null) return false;
        stack.push(); f.animation.applyVanillaUpperBend(stack); return true;
    }
    public static void accessoryChest(LivingEntity entity, MatrixStack stack) {
        Frame f=current(entity);
        if(f != null && f.session != null) f.animation.applyVanillaChestBend(stack, ((PlayerEntityModel<?>)f.renderer.getModel()).body);
    }
    public static void end(LivingEntity player, LivingEntityRenderer<?, ?> renderer) {
        ArrayDeque<Frame> frames=FRAMES.get(); Frame f=frames.peek();
        if(f == null || f.player != player || f.renderer != renderer) return;
        frames.pop();
        try {if(f.session != null)f.session.close();}
        finally {f.release();FREE_FRAMES.get().addFirst(f);}
        // The empty queue holds no entity references and can be reused next frame.
    }
}
