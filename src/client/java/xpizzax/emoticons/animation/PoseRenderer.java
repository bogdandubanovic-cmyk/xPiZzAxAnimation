package xpizzax.emoticons.animation;
import java.util.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import dev.kosmx.playerAnim.core.util.SetableSupplier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import xpizzax.emoticons.utils.MatrixStackUtils;
import xpizzax.emoticons.studio.CinematicStudio;
public final class PoseRenderer {
    private final Map<String, Vec3f> xpizzaxPlayerAnimatorBends = new HashMap<>();
    /* v6.59: feed the COMPLETE BOBJ proxy pose through AnimationApplier, not only bend.
     * This is the key difference from v6.47-v6.58: the exact Dynamix armor model that
     * is about to render receives POSITION + ROTATION + BEND from one PlayerAnimator
     * processor after Dynamix copyBipedStateTo(). */
    private final Map<String, Vec3f> xpizzaxPlayerAnimatorPositions = new HashMap<>();
    private final Map<String, Vec3f> xpizzaxPlayerAnimatorRotations = new HashMap<>();
    private final IAnimation xpizzaxBendAnimation = new IAnimation()
    {
        @Override
        public boolean isActive()
        {
            return true;
        }

        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0)
        {
            if (type == TransformType.BEND)
            {
                Vec3f bend = xpizzaxPlayerAnimatorBends.get(modelName);
                return bend == null ? Vec3f.ZERO : bend;
            }
            if (type == TransformType.POSITION)
            {
                Vec3f position = xpizzaxPlayerAnimatorPositions.get(modelName);
                return position == null ? value0 : position;
            }
            if (type == TransformType.ROTATION)
            {
                Vec3f rotation = xpizzaxPlayerAnimatorRotations.get(modelName);
                return rotation == null ? value0 : rotation;
            }
            return value0;
        }

        @Override
        public void setupAnim(float tickDelta)
        {}
    };
    private final AnimationApplier xpizzaxBendProcessor = new AnimationApplier(this.xpizzaxBendAnimation);
    private final SetableSupplier<AnimationProcessor> xpizzaxBendSupplier = new SetableSupplier<>();
    /* v6.50: Dynamix copies the player model into its armor model INSIDE renderArmor.
     * We expose the active BOBJ bend processor only for that render window, so our
     * BipedEntityModel mixin can apply bend AFTER the copy, at the same point the
     * actual Dynamix armor model is ready to render. */
    private static final ThreadLocal<AnimationApplier> XPIZZAX_ACTIVE_BEND = new ThreadLocal<>();
    private static final ThreadLocal<SetableSupplier<AnimationProcessor>> XPIZZAX_ACTIVE_BEND_SUPPLIER = new ThreadLocal<>();
    /* v6.52: Dynamix owns its own context/armor models. Keep the active BOBJ renderer
     * available while ArmorFeatureRenderer copies its model so the copied armor gets
     * the same torso/arm/leg pose, not only the bend channel. */
    private PlayerEntityModel<?> xpizzaxCaptureModel;

    private static final Matrix4f XPIZZAX_FEATURE_SPACE = new Matrix4f()
            .rotateZ((float) Math.PI).translate(0F, -1.501F, 0F);
    private static final Matrix4f XPIZZAX_INVERSE_FEATURE_SPACE = new Matrix4f(XPIZZAX_FEATURE_SPACE).invert();
    private final Matrix4f xpizzaxRootTransform = new Matrix4f();
    private final Matrix4f xpizzaxInverseRootScratch = new Matrix4f();
    private final Matrix4f xpizzaxInverseUpperScratch = new Matrix4f();
    private final Matrix4f xpizzaxArmorDeltaScratch = new Matrix4f();
    private final Matrix4f xpizzaxUpperDeltaScratch = new Matrix4f();
    private final Vector3f xpizzaxPivotScratch = new Vector3f();
    private final Vector3f xpizzaxEulerScratch = new Vector3f();
    private final MatrixStack upperStackScratch = new MatrixStack();
    private final MatrixStack chestStackScratch = new MatrixStack();
    private final Matrix4f chestInverseScratch = new Matrix4f();
    private final Matrix4f chestDeltaScratch = new Matrix4f();
    // Shared sequential scratch: the hand and torso bend paths never nest.
    // This mirrors IBendHelper.rotateMatrixStack without allocating a Pair,
    // Vector3f and Quaternionf for every rendered player.
    private final org.joml.Quaternionf bendRotationScratch = new org.joml.Quaternionf();
    private final Vector3f bendAxisScratch = new Vector3f();
    private final Map<String,Vec3f> jointBendCache = new HashMap<>();
    private final SpyglassAim spyglassAim = new SpyglassAim();
    private final FirstPersonUseArmPose firstPersonUseArmPose = new FirstPersonUseArmPose();
    private final float[] animatedAimHead = new float[6];
    private final float[] vanillaAimArm = new float[6];

    private net.minecraft.util.Arm xpizzaxVanillaUseArm;
    private final java.util.ArrayDeque<VanillaPoseSession> xpizzaxPoseSessions = new java.util.ArrayDeque<>();
    public static final class VanillaPoseSession implements AutoCloseable {
        private final java.util.ArrayDeque<VanillaPoseSession> pool;
        private IMutableModel model;
        private SetableSupplier<AnimationProcessor> previous;
        private AnimationApplier previousBend;
        private SetableSupplier<AnimationProcessor> previousActive;
        private final ModelPart[] parts = new ModelPart[12];
        private final float[] saved = new float[12 * 9];
        private boolean closed;
        private static final String[] CHANNELS = {"torso", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg",
                "rightArm", "leftArm", "rightLeg", "leftLeg"};
        private VanillaPoseSession(java.util.ArrayDeque<VanillaPoseSession> pool) { this.pool = pool; }
        private void capture(PlayerEntityModel<?> player) {
            this.closed = false;
            this.model = (IMutableModel) (Object) player;
            this.previous = this.model.getEmoteSupplier();
            this.previousBend = XPIZZAX_ACTIVE_BEND.get();
            this.previousActive = XPIZZAX_ACTIVE_BEND_SUPPLIER.get();
            parts[0]=player.head; parts[1]=player.hat; parts[2]=player.body; parts[3]=player.jacket;
            parts[4]=player.rightArm; parts[5]=player.leftArm; parts[6]=player.rightLeg; parts[7]=player.leftLeg;
            parts[8]=player.rightSleeve; parts[9]=player.leftSleeve; parts[10]=player.rightPants; parts[11]=player.leftPants;
            for (int i=0; i<parts.length; i++) {
                ModelPart p=parts[i]; int o=i*9;
                saved[o]=p.pivotX; saved[o+1]=p.pivotY; saved[o+2]=p.pivotZ;
                saved[o+3]=p.pitch; saved[o+4]=p.yaw; saved[o+5]=p.roll;
                saved[o+6]=p.xScale; saved[o+7]=p.yScale; saved[o+8]=p.zScale;
            }
        }
        private void restorePart(int i) {
            ModelPart p=parts[i]; int o=i*9;
            p.pivotX=saved[o]; p.pivotY=saved[o+1]; p.pivotZ=saved[o+2];
            p.pitch=saved[o+3]; p.yaw=saved[o+4]; p.roll=saved[o+5];
            p.xScale=saved[o+6]; p.yScale=saved[o+7]; p.zScale=saved[o+8];
        }
        @Override public void close() {
            if (this.closed) return;
            this.closed = true;
            this.model.setEmoteSupplier(this.previous);
            for (int i=2; i<this.parts.length; i++) {
                if (this.previousBend == null) IBendHelper.INSTANCE.bend(this.parts[i], 0F, 0F);
                else this.previousBend.updatePart(CHANNELS[i-2], this.parts[i]);
            }
            for (int i=0; i<this.parts.length; i++) { this.restorePart(i); this.parts[i]=null; }
            if (this.previousBend == null) XPIZZAX_ACTIVE_BEND.remove(); else XPIZZAX_ACTIVE_BEND.set(this.previousBend);
            if (this.previousActive == null) XPIZZAX_ACTIVE_BEND_SUPPLIER.remove(); else XPIZZAX_ACTIVE_BEND_SUPPLIER.set(this.previousActive);
            this.model=null; this.previous=null; this.previousBend=null; this.previousActive=null;
            this.pool.addFirst(this);
        }
    }

    private static final ClassValue<Boolean> RPM_GENDER_FEATURE = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> featureType) {
            if (!featureType.getName().startsWith("RPMC.")) return false;
            for (Class<?> type=featureType; type!=null && type!=Object.class; type=type.getSuperclass()) {
                for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                    String name=method.getName();
                    if ("setupRender".equals(name) || "resizeBox".equals(name) || "renderBreastArmor".equals(name)) return true;
                }
            }
            return false;
        }
    };

    private final Rig rig;
    private final Animator animator=new Animator();
    private final Map<String,Matrix4f> deltas=new HashMap<>();
    private final Map<String,Matrix4f> inverseBinds=new HashMap<>();
    private int lastTick=Integer.MIN_VALUE;
    private static final String[] BONES={"body","low_body","head","right_arm","left_arm","right_leg","left_leg"};
    public PoseRenderer(String skin) {
        rig=new Rig(skin); animator.setup(rig,new ActionsConfig(),false);
        for(String name:BONES){Rig.Bone bone=rig.bones.get(name);if(bone!=null){deltas.put(name,new Matrix4f());inverseBinds.put(name,new Matrix4f().rotateY((float)Math.PI).mul(bone.boneMat).invert());}}
    }
    public void tick(LivingEntity entity) {
        if(entity.age==lastTick)return;
        // Replay seeks rewind entity age; reset the state machine rather than reusing a future pose.
        if(entity.age<lastTick){animator.prevX=Float.MAX_VALUE;animator.resetMotionGate();animator.actions.clear();animator.active=null;animator.lastActive=null;animator.setup(rig,new ActionsConfig(),false);}
        lastTick=entity.age; animator.update(entity);
    }
    public boolean prepareVanillaPose(LivingEntity entity,float delta,float headYaw,float headPitch) {
        return prepareVanillaPose(entity,delta,headYaw,headPitch,false);
    }
    public boolean prepareVanillaPose(LivingEntity entity,float delta,float headYaw,float headPitch,boolean firstPerson) {
        delta=Float.isFinite(delta)?Math.max(0F,Math.min(1F,delta)):0F;
        rig.setHeadAngles(headYaw,headPitch);
        // Never let a render-only FirstPerson position offset enter motion
        // detection. The client tick continues this same rig in every view.
        if(!firstPerson)tick(entity);
        // Catch interactions that begin between END_CLIENT_TICK callbacks; the
        // vanilla model has already computed this frame's partial-tick context.
        rig.resetPose();animator.applyActions(entity,rig,delta);
        CinematicStudio.applyToRig(rig, entity);
        rig.setupMatrices();
        for(String name:BONES){Rig.Bone bone=rig.bones.get(name);if(bone!=null)deltas.get(name).identity().rotateY((float)Math.PI).mul(bone.mat).mul(inverseBinds.get(name));}
        return true;
    }
    private Matrix4f xpizzaxVanillaSkinDelta(String name){return deltas.get(name);}
    private Matrix4f xpizzaxBobjSkinDelta(String name){return deltas.get(name);}
    private Vec3f computeBobjBend(String child,String parent) {
        Rig.Bone joint=rig.bones.get(child);if(joint==null)return Vec3f.ZERO;
        float angle=joint.transform.rotate.x;
        if(!Float.isFinite(angle)||Math.abs(angle)<1e-4F)return Vec3f.ZERO;
        Vec3f cached=jointBendCache.get(child);
        if(cached==null||cached.getY()!=-angle){cached=new Vec3f(0,-angle,0);jointBendCache.put(child,cached);}
        return cached;
    }
    public void applyVanillaRoot(MatrixStack stack) {
        Matrix4f root = this.xpizzaxVanillaSkinDelta("body");
        if (root == null) return;
        MatrixStackUtils.multiply(stack, this.xpizzaxRootTransform
                .set(XPIZZAX_INVERSE_FEATURE_SPACE).mul(root).mul(XPIZZAX_FEATURE_SPACE));
    }
    public VanillaPoseSession applyVanillaPose(PlayerEntityModel<?> vanilla, float tickDelta, LivingEntity entity) {
        VanillaPoseSession session = this.xpizzaxPoseSessions.pollFirst();
        if (session == null) session = new VanillaPoseSession(this.xpizzaxPoseSessions);
        session.capture(vanilla);
        this.syncRpmVanillaSkeleton(vanilla);
        this.xpizzaxCaptureModel = vanilla;
        this.updatePlayerAnimatorBends();
        this.xpizzaxVanillaUseArm = null;
        if (entity != null && entity.isUsingItem()) {
            // Preserve Minecraft's item-use pose, including the raised spyglass arm.
            boolean main = entity.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND;
            net.minecraft.util.Arm arm = main ? entity.getMainArm() : entity.getMainArm().getOpposite();
            this.xpizzaxVanillaUseArm = arm;
            int index = arm == net.minecraft.util.Arm.RIGHT ? 4 : 5;
            session.restorePart(index);
            net.minecraft.util.UseAction use = entity.getActiveItem().getUseAction();
            if (entity.getActiveItem().isOf(net.minecraft.item.Items.SPYGLASS)
                    || use == net.minecraft.util.UseAction.EAT || use == net.minecraft.util.UseAction.DRINK) {
                // Mouth/eye actions keep the animated head and its waist-bend
                // compensation. Restoring the crouched native pivot would lower
                // the head twice. Move the native use arm into this head frame.
                ModelPart aimingArm = arm == net.minecraft.util.Arm.RIGHT ? vanilla.rightArm : vanilla.leftArm;
                readAimPose(vanilla.head, animatedAimHead);
                readAimPose(aimingArm, vanillaAimArm);
                if ((use == net.minecraft.util.UseAction.EAT || use == net.minecraft.util.UseAction.DRINK)
                        && xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge.isFirstPersonBody(entity)) {
                    // FirstPerson's camera stays at the native eye position.
                    // Aiming at our bent/hidden head pushes the use arm behind
                    // that camera. Keep the original camera-space arm instead.
                    Matrix4f inverseRoot = xpizzaxRootTransform.set(XPIZZAX_INVERSE_FEATURE_SPACE)
                            .mul(xpizzaxInverseRootScratch).mul(XPIZZAX_FEATURE_SPACE);
                    firstPersonUseArmPose.compensate(xpizzaxInverseUpperScratch, inverseRoot, vanillaAimArm);
                } else {
                    spyglassAim.align(session.saved, animatedAimHead, vanillaAimArm);
                }
                aimingArm.setPivot(vanillaAimArm[0], vanillaAimArm[1], vanillaAimArm[2]);
                aimingArm.pitch = vanillaAimArm[3]; aimingArm.yaw = vanillaAimArm[4]; aimingArm.roll = vanillaAimArm[5];
            } else {
                session.restorePart(0);
            }
            String channel = arm == net.minecraft.util.Arm.RIGHT ? "rightArm" : "leftArm";
            this.xpizzaxPlayerAnimatorBends.put(channel, Vec3f.ZERO);
            this.capturePlayerAnimatorPart(channel, arm == net.minecraft.util.Arm.RIGHT ? vanilla.rightArm : vanilla.leftArm);
            this.capturePlayerAnimatorPart("head", vanilla.head);
        }
        this.xpizzaxBendProcessor.setTickDelta(tickDelta);
        this.xpizzaxBendSupplier.set(this.xpizzaxBendProcessor);
        ((IMutableModel) (Object) vanilla).setEmoteSupplier(this.xpizzaxBendSupplier);
        XPIZZAX_ACTIVE_BEND.set(this.xpizzaxBendProcessor);
        XPIZZAX_ACTIVE_BEND_SUPPLIER.set(this.xpizzaxBendSupplier);
        this.xpizzaxBendProcessor.updatePart("torso", vanilla.body);
        this.xpizzaxBendProcessor.updatePart("rightArm", vanilla.rightArm);
        this.xpizzaxBendProcessor.updatePart("leftArm", vanilla.leftArm);
        this.xpizzaxBendProcessor.updatePart("rightLeg", vanilla.rightLeg);
        this.xpizzaxBendProcessor.updatePart("leftLeg", vanilla.leftLeg);
        vanilla.hat.copyTransform(vanilla.head);
        vanilla.jacket.copyTransform(vanilla.body);
        vanilla.rightSleeve.copyTransform(vanilla.rightArm);
        vanilla.leftSleeve.copyTransform(vanilla.leftArm);
        vanilla.rightPants.copyTransform(vanilla.rightLeg);
        vanilla.leftPants.copyTransform(vanilla.leftLeg);
        this.xpizzaxBendProcessor.updatePart("torso", vanilla.jacket);
        this.xpizzaxBendProcessor.updatePart("rightArm", vanilla.rightSleeve);
        this.xpizzaxBendProcessor.updatePart("leftArm", vanilla.leftSleeve);
        this.xpizzaxBendProcessor.updatePart("rightLeg", vanilla.rightPants);
        this.xpizzaxBendProcessor.updatePart("leftLeg", vanilla.leftPants);
        return session;
    }
    private static void readAimPose(ModelPart part, float[] pose) {
        pose[0] = part.pivotX; pose[1] = part.pivotY; pose[2] = part.pivotZ;
        pose[3] = part.pitch; pose[4] = part.yaw; pose[5] = part.roll;
    }
    private void rotateBend(MatrixStack stack, Vec3f bend, float pivotY) {
        if (Math.abs(bend.getY()) < 1.0E-5F) return;
        float direction = -bend.getX();
        stack.translate(0F, pivotY, 0F);
        stack.multiply(bendRotationScratch.identity().rotateAxis(bend.getY(),
                bendAxisScratch.set((float) Math.cos(direction), 0F, (float) Math.sin(direction))));
        stack.translate(0F, -pivotY, 0F);
    }
    public void applyVanillaHandBend(MatrixStack stack, net.minecraft.util.Arm arm) {
        if (arm == this.xpizzaxVanillaUseArm) return;
        boolean left = arm == net.minecraft.util.Arm.LEFT;
        Vec3f bend = this.computeBobjBend(left ? "low_left_arm" : "low_right_arm",
                left ? "left_arm" : "right_arm");
        this.rotateBend(stack, bend, 0.25F);
    }
    public void applyVanillaUpperBend(MatrixStack stack) {
        this.rotateBend(stack, this.computeBobjBend("low_body", "body"), 0.375F);
    }
    public void applyVanillaChestBend(MatrixStack stack, ModelPart body) {
        MatrixStack local = chestStackScratch;
        local.loadIdentity();
        body.rotate(local);
        Matrix4f inverse = chestInverseScratch.set(local.peek().getPositionMatrix()).invert();
        this.rotateBend(local, this.computeBobjBend("low_body", "body"), 0.375F);
        MatrixStackUtils.multiply(stack, chestDeltaScratch.set(local.peek().getPositionMatrix()).mul(inverse));
    }
    private void syncRpmVanillaSkeleton(PlayerEntityModel<?> vanilla)
    {
        Matrix4f rootDelta = this.xpizzaxBobjSkinDelta("body");
        if (rootDelta == null)
        {
            return;
        }

        this.resetVanillaBindPivots(vanilla);
        Matrix4f inverseRoot = this.xpizzaxInverseRootScratch.set(rootDelta).invert();
        Matrix4f featureSpace = XPIZZAX_FEATURE_SPACE;
        Matrix4f inverseFeature = XPIZZAX_INVERSE_FEATURE_SPACE;

        /* BipedEntityModelMixin renders head/arms under this additional torso stack.
         * Read the SAME helper transform rather than baking that torso movement into
         * their pivots a second time. Body geometry receives the corresponding bend. */
        /* Use the same waist bend for skeleton compensation and armor deformation.
         * This keeps the upper parts from receiving the torso bend twice. */
        Vec3f torsoBend = this.computeBobjBend("low_body", "body");
        MatrixStack upperStack = upperStackScratch;
        upperStack.loadIdentity();
        this.rotateBend(upperStack, torsoBend, 0.375F);
        Matrix4f inverseUpper = this.xpizzaxInverseUpperScratch
                .set(upperStack.peek().getPositionMatrix()).invert();

        Matrix4f torso = this.xpizzaxArmorDelta("low_body", inverseRoot, featureSpace, inverseFeature);
        if (torso != null) this.xpizzaxApplyPartDelta(vanilla.body, torso.mul(inverseUpper));
        this.xpizzaxSyncArmorPart(vanilla.head, "head", inverseRoot, featureSpace, inverseFeature, inverseUpper);
        this.xpizzaxSyncArmorPart(vanilla.rightArm, "right_arm", inverseRoot, featureSpace, inverseFeature, inverseUpper);
        this.xpizzaxSyncArmorPart(vanilla.leftArm, "left_arm", inverseRoot, featureSpace, inverseFeature, inverseUpper);
        this.xpizzaxSyncArmorPart(vanilla.rightLeg, "right_leg", inverseRoot, featureSpace, inverseFeature, null);
        this.xpizzaxSyncArmorPart(vanilla.leftLeg, "left_leg", inverseRoot, featureSpace, inverseFeature, null);

        vanilla.hat.copyTransform(vanilla.head);
        vanilla.jacket.copyTransform(vanilla.body);
        vanilla.rightSleeve.copyTransform(vanilla.rightArm);
        vanilla.leftSleeve.copyTransform(vanilla.leftArm);
        vanilla.rightPants.copyTransform(vanilla.rightLeg);
        vanilla.leftPants.copyTransform(vanilla.leftLeg);
    }
    private Matrix4f xpizzaxArmorDelta(String bone, Matrix4f inverseRoot,
                                      Matrix4f feature, Matrix4f inverseFeature)
    {
        Matrix4f skinDelta = this.xpizzaxBobjSkinDelta(bone);
        if (skinDelta == null) return null;
        /* The external feature stack already applies the root once. Conjugation
         * accounts for Z180 AND the vanilla height translation, including the pivot. */
        return this.xpizzaxArmorDeltaScratch.set(inverseFeature).mul(inverseRoot).mul(skinDelta).mul(feature);
    }
    private void xpizzaxSyncArmorPart(ModelPart part, String bone, Matrix4f inverseRoot,
                                      Matrix4f feature, Matrix4f inverseFeature, Matrix4f inverseUpper)
    {
        Matrix4f delta = this.xpizzaxArmorDelta(bone, inverseRoot, feature, inverseFeature);
        if (delta == null) return;
        if (inverseUpper != null) delta = this.xpizzaxUpperDeltaScratch.set(inverseUpper).mul(delta);
        this.xpizzaxApplyPartDelta(part, delta);
    }
    private void xpizzaxApplyPartDelta(ModelPart part, Matrix4f delta)
    {
        /* A deformation matrix rotates about the bone's authored joint. Apply it to
         * the bind pivot ONCE; its translation alone is not the animated joint position.
         * The pivot is pixels while the skin/feature matrices are in blocks. */
        Vector3f pivot = this.xpizzaxPivotScratch.set(part.pivotX / 16.0F, part.pivotY / 16.0F, part.pivotZ / 16.0F);
        delta.transformPosition(pivot);
        Vector3f euler = delta.getEulerAnglesZYX(this.xpizzaxEulerScratch);
        part.setPivot(pivot.x * 16.0F, pivot.y * 16.0F, pivot.z * 16.0F);
        part.pitch = euler.x;
        part.yaw = euler.y;
        part.roll = euler.z;
    }
    private void resetVanillaBindPivots(PlayerEntityModel<?> vanilla)
    {
        vanilla.head.setPivot(0F, 0F, 0F);
        vanilla.hat.setPivot(0F, 0F, 0F);
        vanilla.body.setPivot(0F, 0F, 0F);
        vanilla.rightArm.setPivot(-5F, 2F, 0F);
        vanilla.leftArm.setPivot(5F, 2F, 0F);
        vanilla.rightLeg.setPivot(-1.9F, 12F, 0F);
        vanilla.leftLeg.setPivot(1.9F, 12F, 0F);
    }
    public static boolean isRpmGenderFeature(Object feature) {
        return feature != null && RPM_GENDER_FEATURE.get(feature.getClass());
    }
    private void updatePlayerAnimatorBends()
    {

        /* v6.60: local BOBJ bend channels are converted to vanilla joint axes.
         * Positions/rotations come from the full bind-to-pose deformation above. */
        Vec3f torsoBend = this.computeBobjBend("low_body", "body");
        this.xpizzaxPlayerAnimatorBends.put("body", torsoBend);
        /* AnimationApplier.updatePart("torso") adds torso + body bends.
         * BipedEntityModel renders upper parts with body only. Publishing the
         * waist twice made chest geometry bend twice while shoulders moved once. */
        this.xpizzaxPlayerAnimatorBends.put("torso", Vec3f.ZERO);
        this.xpizzaxPlayerAnimatorBends.put("rightArm", this.computeBobjBend("low_right_arm", "right_arm"));
        this.xpizzaxPlayerAnimatorBends.put("leftArm", this.computeBobjBend("low_left_arm", "left_arm"));
        /* v6.60: the full coordinate conversion preserves anatomical sides. */
        this.xpizzaxPlayerAnimatorBends.put("rightLeg", this.computeBobjBend("low_leg_right", "right_leg"));
        this.xpizzaxPlayerAnimatorBends.put("leftLeg", this.computeBobjBend("low_left_leg", "left_leg"));

        /* Capture the already BOBJ-synchronised vanilla proxy as PlayerAnimator values.
         * AnimationApplier writes these exact values to Dynamix's real inner/outer model
         * AFTER Dynamix has copied its context state, so jacket/sleeves/shoes cannot stay
         * behind at the vanilla pose. */
        this.capturePlayerAnimatorPart("head", this.xpizzaxCaptureModel.head);
        this.capturePlayerAnimatorPart("torso", this.xpizzaxCaptureModel.body);
        this.capturePlayerAnimatorPart("rightArm", this.xpizzaxCaptureModel.rightArm);
        this.capturePlayerAnimatorPart("leftArm", this.xpizzaxCaptureModel.leftArm);
        this.capturePlayerAnimatorPart("rightLeg", this.xpizzaxCaptureModel.rightLeg);
        this.capturePlayerAnimatorPart("leftLeg", this.xpizzaxCaptureModel.leftLeg);
    }
    private void capturePlayerAnimatorPart(String name, ModelPart part)
    {
        if (part == null) return;
        cacheVector(this.xpizzaxPlayerAnimatorPositions,name,part.pivotX,part.pivotY,part.pivotZ);
        cacheVector(this.xpizzaxPlayerAnimatorRotations,name,part.pitch,part.yaw,part.roll);
    }
    private static void cacheVector(Map<String,Vec3f> cache,String key,float x,float y,float z) {
        Vec3f old=cache.get(key);
        if(old==null||old.getX()!=x||old.getY()!=y||old.getZ()!=z)cache.put(key,new Vec3f(x,y,z));
    }
    public static void xpizzaxApplyActiveBendToCopiedArmor(BipedEntityModel<?> armor)
    {
        ArmorPoseBridge.apply(armor, XPIZZAX_ACTIVE_BEND.get(), XPIZZAX_ACTIVE_BEND_SUPPLIER.get());
    }
}
