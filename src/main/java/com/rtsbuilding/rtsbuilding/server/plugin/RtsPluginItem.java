package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/**
 * 可真实安装的插件物品。
 *
 * <p>本类只把右击动作适配到权威服务；安装合法性、物品消耗、持久化和替换退回仍由
 * {@link RtsPluginService} 负责，不能在客户端预先扣除或复制物品。</p>
 */
public class RtsPluginItem extends Item {
    private static final String REMOTE_CONTROL_PLUGIN = "remote_control_plugin";
    private static final String STORAGE_INTEGRATION_PLUGIN = "storage_integration_plugin";
    private static final String AREA_DESTROY_PLUGIN = "area_destroy_plugin";

    /** 1.12.2 Item 没有 Properties；堆叠数和创造栏由集中注册器设置。 */
    public RtsPluginItem() {
        super();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote && player instanceof EntityPlayerMP
                && RtsPluginService.installHeldPlugin((EntityPlayerMP) player, hand)) {
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }
        return new ActionResult<>(EnumActionResult.PASS, held);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        ResourceLocation itemId = stack.getItem().getRegistryName();
        if (itemId == null || !RtsbuildingMod.MODID.equals(itemId.getNamespace())) return;

        String pluginPath = itemId.getPath();
        tooltip.add(TextFormatting.GRAY + localize("tooltip.rtsbuilding.plugin." + pluginPath));
        appendDependencyTooltip(pluginPath, tooltip);
    }

    @SideOnly(Side.CLIENT)
    private static void appendDependencyTooltip(String pluginPath, List<String> tooltip) {
        List<String> dependencies = dependenciesFor(pluginPath);
        if (dependencies.isEmpty()) return;
        if (!ClientKeyState.isControlDown()) {
            tooltip.add(TextFormatting.DARK_GRAY
                    + localize("tooltip.rtsbuilding.plugin.dependencies.hold_ctrl"));
            return;
        }
        tooltip.add(TextFormatting.DARK_GRAY
                + localize("tooltip.rtsbuilding.plugin.dependencies.title"));
        for (String dependency : dependencies) {
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocalFormatted(
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
        return colorFor(pluginPath) + localize("item.rtsbuilding." + pluginPath) + TextFormatting.RESET;
    }

    private static TextFormatting colorFor(String pluginPath) {
        if (REMOTE_CONTROL_PLUGIN.equals(pluginPath)) return TextFormatting.AQUA;
        if (STORAGE_INTEGRATION_PLUGIN.equals(pluginPath)) return TextFormatting.GREEN;
        return TextFormatting.GOLD;
    }

    private static String localize(String key) {
        return I18n.translateToLocal(key);
    }

    /** 该嵌套类只会在 tooltip 的客户端调用路径中加载。 */
    @SideOnly(Side.CLIENT)
    private static final class ClientKeyState {
        private ClientKeyState() {
        }

        private static boolean isControlDown() {
            return FMLCommonHandler.instance().getSide().isClient()
                    && net.minecraft.client.gui.GuiScreen.isCtrlKeyDown();
        }
    }
}
