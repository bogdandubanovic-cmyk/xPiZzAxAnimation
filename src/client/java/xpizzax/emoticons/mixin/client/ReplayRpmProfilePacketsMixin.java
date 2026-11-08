package xpizzax.emoticons.mixin.client;

import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import xpizzax.emoticons.client.replay.RpmGenderReplay;

/** Supplied RPM 7.1 profile packets: put/remove are authoritative; clear is only a cache reset. */
@Pseudo
@Mixin(targets="RPMC.GBRPMC\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200c\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200d\u200c\u200e\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200f\u200c\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200d\u200c\u200d\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200f\u200c\u200c\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200e\u200b\u200f\u200b\u200b\u200b\u200b\u200b\u200b\u200b.GBRPMC\u200b\u200d\u200d\u200c\u200c\u200b\u200b\u200b\u200b\u200b",remap=false)
public abstract class ReplayRpmProfilePacketsMixin {
    @Redirect(method="*",at=@At(value="INVOKE",target="Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),require=0,remap=false)
    private Object rpmmodels$profile(Map<Object,Object> cache,Object id,Object model){
        Object old=cache.put(id,model);RpmGenderReplay.profileReceived(id,model);return old;
    }
    @Redirect(method="*",at=@At(value="INVOKE",target="Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"),require=0,remap=false)
    private Object rpmmodels$male(Map<Object,Object> cache,Object id){
        Object old=cache.remove(id);RpmGenderReplay.profileReceived(id,null);return old;
    }
}
