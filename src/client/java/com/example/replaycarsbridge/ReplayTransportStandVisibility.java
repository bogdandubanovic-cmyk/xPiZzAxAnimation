package com.example.replaycarsbridge;

import me.wattguy.cars.infos.Car;
import me.wattguy.cars.imixins.ICarEntity;
import net.example.replaynv.ReplayState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;

/** Hide wooden bodies of Cars vehicle parts, including bikes, only during Replay playback/export. */
public final class ReplayTransportStandVisibility {
    private static final boolean CARS_PRESENT=FabricLoader.getInstance().isModLoaded("cars");
    private static boolean compatible=true;

    private ReplayTransportStandVisibility() {}

    public static boolean hideBody(ArmorStandEntity stand) {
        if(!CARS_PRESENT || !compatible || stand==null || !ReplayState.isReplayOpen())return false;
        try {
            return CarsParts.contains(stand);
        } catch(LinkageError e) {
            compatible=false;
            System.err.println("[xPiZzAxAnimation] Cars vehicle part visibility API is unavailable: "+e);
            return false;
        }
    }

    /** Resolved only after the loader check, so Cars remains an optional dependency. */
    private static final class CarsParts {
        private static boolean contains(ArmorStandEntity stand) {
            int id=stand.getId();
            // Cars registers every vehicle ID here: main, skin, seats and wheels.
            // The registry is independent of vehicle type and wheel count.
            Car car=Car.getAll().get(id);
            // Also cover parts attached through Car.set before/without a registry entry.
            if(car==null && (Object)stand instanceof ICarEntity part)car=part.getCars$car();
            if(car==null)return false;
            Entity main=car.getMainEntity();
            // Guard against a stale registry from a previous world/replay with reused entity IDs.
            return main!=null && main.getWorld()==stand.getWorld()
                    && stand.getWorld().getEntityById(id)==stand;
        }
    }
}
