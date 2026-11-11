package xpizzax.emoticons.studio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Deterministic interpolation for camera, body, eyes, bones and captured Emotecraft poses. */
public final class StudioInterpolator {
    private StudioInterpolator() {}

    public static StudioFrame sample(StudioProject project, float tick) {
        List<StudioFrame> frames = new ArrayList<>();
        for (StudioFrame frame : project.frames) if (frame.keyframe) frames.add(frame);
        if (frames.size() < 2) frames = project.frames;
        if (frames.isEmpty()) return null;
        StudioFrame first = frames.get(0), last = frames.get(frames.size() - 1);
        if (tick <= first.tick) return copy(first);
        if (tick >= last.tick) return copy(last);
        StudioFrame a = first, b = last;
        for (int i = 1; i < frames.size(); i++) {
            if (frames.get(i).tick >= tick) { a = frames.get(i - 1); b = frames.get(i); break; }
        }
        float span = Math.max(1, b.tick - a.tick);
        float t = clamp((tick - a.tick) / span, 0F, 1F);
        StudioFrame out = new StudioFrame();
        out.tick = Math.round(tick);
        out.x = lerp(a.x, b.x, t); out.y = lerp(a.y, b.y, t); out.z = lerp(a.z, b.z, t);
        out.bodyYaw = angle(a.bodyYaw, b.bodyYaw, t);
        out.headYaw = angle(a.headYaw, b.headYaw, t);
        out.headPitch = lerp(a.headPitch, b.headPitch, t);
        out.cameraX = lerp(a.cameraX, b.cameraX, t);
        out.cameraY = lerp(a.cameraY, b.cameraY, t);
        out.cameraZ = lerp(a.cameraZ, b.cameraZ, t);
        out.cameraYaw = angle(a.cameraYaw, b.cameraYaw, t);
        out.cameraPitch = lerp(a.cameraPitch, b.cameraPitch, t);
        out.cameraFov = lerp(a.cameraFov, b.cameraFov, t);
        out.eyes.lookYaw = angle(a.eyes.lookYaw, b.eyes.lookYaw, t);
        out.eyes.lookPitch = lerp(a.eyes.lookPitch, b.eyes.lookPitch, t);
        out.eyes.openness = clamp(lerp(a.eyes.openness, b.eyes.openness, t), 0F, 1F);
        out.eyes.expression = t < 0.5F ? a.eyes.expression : b.eyes.expression;

        Set<String> names = new LinkedHashSet<>(a.bones.keySet());
        names.addAll(b.bones.keySet());
        for (String name : names) {
            StudioFrame.BonePose pa = a.bones.get(name), pb = b.bones.get(name);
            if (pa == null) pa = new StudioFrame.BonePose();
            if (pb == null) pb = new StudioFrame.BonePose();
            StudioFrame.BonePose p = new StudioFrame.BonePose();
            p.x=lerp(pa.x,pb.x,t); p.y=lerp(pa.y,pb.y,t); p.z=lerp(pa.z,pb.z,t);
            p.pitch=angle(pa.pitch,pb.pitch,t); p.yaw=angle(pa.yaw,pb.yaw,t); p.roll=angle(pa.roll,pb.roll,t);
            p.scaleX=lerp(pa.scaleX,pb.scaleX,t); p.scaleY=lerp(pa.scaleY,pb.scaleY,t); p.scaleZ=lerp(pa.scaleZ,pb.scaleZ,t);
            out.bones.put(name,p);
        }

        // Emotecraft pose snapshots are discrete renderer samples. Blend all model
        // channels when both sides contain an emote; otherwise choose the nearest take sample.
        if (a.emotecraftPose && b.emotecraftPose) {
            out.emotecraftPose = true;
            Set<String> parts = new LinkedHashSet<>(a.emotecraftModel.keySet());
            parts.addAll(b.emotecraftModel.keySet());
            for (String name : parts) {
                StudioFrame.ModelPose pa = a.emotecraftModel.get(name), pb = b.emotecraftModel.get(name);
                if (pa == null) pa = pb;
                if (pb == null) pb = pa;
                if (pa == null) continue;
                StudioFrame.ModelPose p = new StudioFrame.ModelPose();
                p.pivotX=lerp(pa.pivotX,pb.pivotX,t); p.pivotY=lerp(pa.pivotY,pb.pivotY,t); p.pivotZ=lerp(pa.pivotZ,pb.pivotZ,t);
                p.pitch=lerp(pa.pitch,pb.pitch,t); p.yaw=lerp(pa.yaw,pb.yaw,t); p.roll=lerp(pa.roll,pb.roll,t);
                p.scaleX=lerp(pa.scaleX,pb.scaleX,t); p.scaleY=lerp(pa.scaleY,pb.scaleY,t); p.scaleZ=lerp(pa.scaleZ,pb.scaleZ,t);
                out.emotecraftModel.put(name,p);
            }
        } else {
            StudioFrame nearest = t < 0.5F ? a : b;
            out.emotecraftPose = nearest.emotecraftPose;
            if (out.emotecraftPose) nearest.emotecraftModel.forEach((name, pose) -> out.emotecraftModel.put(name, copy(pose)));
        }
        return out;
    }

    private static StudioFrame copy(StudioFrame source) {
        StudioFrame out = new StudioFrame();
        out.tick=source.tick; out.keyframe=source.keyframe; out.x=source.x; out.y=source.y; out.z=source.z;
        out.bodyYaw=source.bodyYaw; out.headYaw=source.headYaw; out.headPitch=source.headPitch;
        out.cameraX=source.cameraX; out.cameraY=source.cameraY; out.cameraZ=source.cameraZ;
        out.cameraYaw=source.cameraYaw; out.cameraPitch=source.cameraPitch; out.cameraFov=source.cameraFov;
        out.eyes.lookYaw=source.eyes.lookYaw; out.eyes.lookPitch=source.eyes.lookPitch;
        out.eyes.openness=source.eyes.openness; out.eyes.expression=source.eyes.expression;
        source.bones.forEach((name, pose) -> out.bones.put(name, copy(pose)));
        out.emotecraftPose=source.emotecraftPose;
        source.emotecraftModel.forEach((name, pose) -> out.emotecraftModel.put(name, copy(pose)));
        return out;
    }

    private static StudioFrame.BonePose copy(StudioFrame.BonePose source) {
        StudioFrame.BonePose p=new StudioFrame.BonePose();
        p.x=source.x;p.y=source.y;p.z=source.z;p.pitch=source.pitch;p.yaw=source.yaw;p.roll=source.roll;
        p.scaleX=source.scaleX;p.scaleY=source.scaleY;p.scaleZ=source.scaleZ;return p;
    }
    private static StudioFrame.ModelPose copy(StudioFrame.ModelPose source) {
        StudioFrame.ModelPose p=new StudioFrame.ModelPose();
        p.pivotX=source.pivotX;p.pivotY=source.pivotY;p.pivotZ=source.pivotZ;
        p.pitch=source.pitch;p.yaw=source.yaw;p.roll=source.roll;
        p.scaleX=source.scaleX;p.scaleY=source.scaleY;p.scaleZ=source.scaleZ;return p;
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static double lerp(double a,double b,float t){return a+(b-a)*t;}
    private static float angle(float a,float b,float t){float d=((b-a+540F)%360F)-180F;return a+d*t;}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
