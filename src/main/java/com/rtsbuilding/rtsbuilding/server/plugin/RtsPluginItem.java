package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * Real inventory item used to install one RTS plugin.
 *
 * <p>The item only adapts right-click use into the server service. It does not
 * decide install legality or mutate persistent state directly.
 */
public class RtsPluginItem extends Item {
    private static final String REMOTE_CONTROL_PLUGIN = "remote_control_plugin";
    private static final String STORAGE_INTEGRATION_PLUGIN = "storage_integration_plugin";

    public RtsPluginItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (RtsPluginService.installHeldPlugin(serverPlayer, usedHand)) {
                return InteractionResultHolder.sidedSuccess(serverPlayer.getItemInHand(usedHand), false);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && RtsbuildingMod.MODID.equals(itemId.getNamespace())) {
            String pluginPath = itemId.getPath();
            tooltipComponents.add(Component.translatable("tooltip.rtsbuilding.plugin." + pluginPath)
                    .withStyle(ChatFormatting.GRAY));
            appendDependencyTooltip(pluginPath, tooltipComponents);
        }
    }

    private static void appendDependencyTooltip(String pluginPath, List<Component> tooltipComponents) {
        List<String> dependencies = dependenciesFor(pluginPath);
        if (dependencies.isEmpty()) {
            return;
        }
        if (!isControlDown()) {
            tooltipComponents.add(Component.translatable("tooltip.rtsbuilding.plugin.dependencies.hold_ctrl")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltipComponents.add(Component.translatable("tooltip.rtsbuilding.plugin.dependencies.title")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (String dependency : dependencies) {
            tooltipComponents.add(Component.translatable(
                            "tooltip.rtsbuilding.plugin.dependencies.requires",
                            styledPluginName(dependency))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static List<String> dependenciesFor(String pluginPath) {
        return switch (pluginPath) {
            case "chain_break_plugin", "area_destroy_plugin", "blueprint_plugin" -> List.of(REMOTE_CONTROL_PLUGIN);
            case "craft_terminal_plugin" -> List.of(STORAGE_INTEGRATION_PLUGIN);
            default -> List.of();
        };
    }

    private static Component styledPluginName(String pluginPath) {
        return Component.translatable("item.rtsbuilding." + pluginPath)
                .withStyle(colorFor(pluginPath));
    }

    private static ChatFormatting colorFor(String pluginPath) {
        return switch (pluginPath) {
            case REMOTE_CONTROL_PLUGIN -> ChatFormatting.AQUA;
            case STORAGE_INTEGRATION_PLUGIN -> ChatFormatting.GREEN;
            default -> ChatFormatting.GOLD;
        };
    }

    private static boolean isControlDown() {
        return FMLEnvironment.dist == Dist.CLIENT && ClientKeyState.isControlDown();
    }

    private static final class ClientKeyState {
        private ClientKeyState() {
        }

        private static boolean isControlDown() {
            return net.minecraft.client.gui.screens.Screen.hasControlDown();
        }
    }
}
