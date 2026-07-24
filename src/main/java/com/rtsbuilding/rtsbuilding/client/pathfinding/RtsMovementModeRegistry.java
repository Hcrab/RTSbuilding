package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public final class RtsMovementModeRegistry {

    
    private static final List<PrioritizedHandler> HANDLERS = new CopyOnWriteArrayList<>();

    
    private static boolean initialized = false;

    private RtsMovementModeRegistry() {
    }

    
    
    

    
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.ELYTRA, 500));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.FLYING, 400));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.SWIMMING, 300));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.CRAWLING, 200));
        HANDLERS.add(new PrioritizedHandler(BuiltinMovementModes.WALKING, 100));

        
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    
    
    

    
    public static void register(MovementModeHandler handler, int priority) {
        HANDLERS.add(new PrioritizedHandler(handler, priority));
        HANDLERS.sort(Comparator.comparingInt(PrioritizedHandler::priority).reversed());
    }

    
    public static void register(MovementModeHandler handler) {
        register(handler, 50);
    }

    
    
    

    
    public static MovementModeHandler findActive(LocalPlayer player) {
        for (PrioritizedHandler ph : HANDLERS) {
            if (ph.handler().isActive(player)) {
                return ph.handler();
            }
        }
        return null;
    }

    
    public static void fireRegistrationEvent() {
        NeoForge.EVENT_BUS.post(new RegisterMovementModeEvent());
    }

    
    
    

    
    private record PrioritizedHandler(MovementModeHandler handler, int priority) {
    }

    
    public static final class RegisterMovementModeEvent extends net.neoforged.bus.api.Event {
        private RegisterMovementModeEvent() {
        }
    }
}
