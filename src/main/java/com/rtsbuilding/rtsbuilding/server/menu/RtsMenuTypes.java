package com.rtsbuilding.rtsbuilding.server.menu;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registers custom menu types for the RTS mod.
 */
public final class RtsMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, RtsbuildingMod.MODID);

    public static final Supplier<MenuType<RtsCraftTerminalMenu>> RTS_CRAFT_TERMINAL =
            MENU_TYPES.register("rts_craft_terminal",
                    () -> new MenuType<>(RtsCraftTerminalMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }

    private RtsMenuTypes() {
    }
}
