package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/** 可真实安装的插件物品；服务端负责合法性、消耗、持久化和替换退回。 */
public class RtsPluginItem extends Item {
    private static final String REMOTE_CONTROL_PLUGIN = "remote_control_plugin";
    private static final String STORAGE_INTEGRATION_PLUGIN = "storage_integration_plugin";
    private static final String AREA_DESTROY_PLUGIN = "area_destroy_plugin";

    @Override
    public ItemStack onItemRightClick(ItemStack held, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            RtsPluginService.installHeldPlugin((EntityPlayerMP) player, EnumHand.MAIN_HAND);
        }
        return player.getHeldItem();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        ResourceLocation itemId = RtsRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !RtsbuildingMod.MODID.equals(itemId.getResourceDomain())) return;
        String pluginPath = itemId.getResourcePath();
        tooltip.add(EnumChatFormatting.GRAY + localize("tooltip.rtsbuilding.plugin." + pluginPath));
        appendDependencyTooltip(pluginPath, tooltip);
    }

    @SideOnly(Side.CLIENT)
    private static void appendDependencyTooltip(String pluginPath, List tooltip) {
        List<String> dependencies = dependenciesFor(pluginPath);
        if (dependencies.isEmpty()) return;
        if (!net.minecraft.client.gui.GuiScreen.isCtrlKeyDown()) {
            tooltip.add(EnumChatFormatting.DARK_GRAY
                    + localize("tooltip.rtsbuilding.plugin.dependencies.hold_ctrl"));
            return;
        }
        tooltip.add(EnumChatFormatting.DARK_GRAY
                + localize("tooltip.rtsbuilding.plugin.dependencies.title"));
        for (String dependency : dependencies) {
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                    "tooltip.rtsbuilding.plugin.dependencies.requires", styledPluginName(dependency)));
        }
    }

    private static List<String> dependenciesFor(String pluginPath) {
        if ("chain_break_plugin".equals(pluginPath)
                || "area_destroy_plugin".equals(pluginPath)
                || "blueprint_plugin".equals(pluginPath)) {
            return Collections.singletonList(REMOTE_CONTROL_PLUGIN);
        }
        if ("craft_terminal_plugin".equals(pluginPath)) {
            return Collections.singletonList(STORAGE_INTEGRATION_PLUGIN);
        }
        if ("harvest_tier_stone".equals(pluginPath)
                || "harvest_tier_iron".equals(pluginPath)
                || "harvest_tier_diamond".equals(pluginPath)
                || "harvest_tier_unlimited".equals(pluginPath)) {
            return Collections.singletonList(AREA_DESTROY_PLUGIN);
        }
        return Collections.emptyList();
    }

    private static String styledPluginName(String pluginPath) {
        return colorFor(pluginPath) + localize("item.rtsbuilding." + pluginPath)
                + EnumChatFormatting.RESET;
    }

    private static EnumChatFormatting colorFor(String pluginPath) {
        if (REMOTE_CONTROL_PLUGIN.equals(pluginPath)) return EnumChatFormatting.AQUA;
        if (STORAGE_INTEGRATION_PLUGIN.equals(pluginPath)) return EnumChatFormatting.GREEN;
        return EnumChatFormatting.GOLD;
    }

    private static String localize(String key) {
        return StatCollector.translateToLocal(key);
    }
}
