package com.example.replaycarsbridge;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.example.replaynv.ReplayState;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Recording hooks are optional; the Cars mod itself remains independent. */
public final class CarsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!ReplayState.isInstalled()) return false;
        if (mixinClassName.equals("com.example.replaycarsbridge.mixin.RecordVehicleUdpMixin")
            || mixinClassName.equals("com.example.replaycarsbridge.mixin.RecordOutgoingVehicleUdpMixin")) {
            return FabricLoader.getInstance().isModLoaded("cars");
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
