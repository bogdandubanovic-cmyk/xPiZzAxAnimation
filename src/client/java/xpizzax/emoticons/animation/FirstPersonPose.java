package xpizzax.emoticons.animation;

/** Remove camera-crossing root/waist motion only in the first-person body draw. */
public final class FirstPersonPose {
    private FirstPersonPose() {}

    public static void stabilize(Rig rig) {
        reset(rig, "anchor");
        reset(rig, "body");
        reset(rig, "low_body");
    }

    private static void reset(Rig rig, String name) {
        Rig.Bone bone=rig.bones.get(name);
        if(bone!=null)bone.transform.reset();
    }
}
