package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * RTSBuilding 自己的创造栏，保证插件物品在创造模式和测试世界里可直接拿到。
 */
public final class RtsCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RtsbuildingMod.MODID);

    public static final RegistryObject<CreativeModeTab> RTSBUILDING_TAB = CREATIVE_TABS.register(
            "rtsbuilding",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rtsbuilding"))
                    .icon(() -> new ItemStack(RtsItems.RTS_CONTROL_CORE.get()))
                    .displayItems((parameters, output) -> {
                        for (var holder : RtsItems.getCreativeTabItems()) {
                            output.accept(holder.get());
                        }
                    })
                    .build());

    private RtsCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
