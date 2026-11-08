package xpizzax.emoticons.client;

import net.fabricmc.loader.api.FabricLoader;
import java.util.function.BooleanSupplier;

/** The eye setting stays independent of the body rig. EMF reads it each frame. */
public final class EyeAnimationController {
    private EyeAnimationController(){}
    public static boolean enabled(){return AnimationPreferences.eyesEnabled();}
    public static void setEnabled(boolean value){AnimationPreferences.setEyesEnabled(value);}
    public static void register(){
        if(!FabricLoader.getInstance().isModLoaded("entity_model_features"))return;
        try{
            Class<?> api=Class.forName("traben.entity_model_features.EMFAnimationApi");
            api.getMethod("registerSingletonAnimationVariable",String.class,String.class,String.class,BooleanSupplier.class)
                    .invoke(null,"rpmmodels","rpmmodels_eyes","xPiZzAxAnimation",(BooleanSupplier)EyeAnimationController::enabled);
            System.out.println("[xPiZzAxAnimation] Animated eye switch connected to EMF");
        }catch(ReflectiveOperationException|LinkageError e){System.err.println("[xPiZzAxAnimation] EMF eye switch unavailable: "+e);}
    }
}
