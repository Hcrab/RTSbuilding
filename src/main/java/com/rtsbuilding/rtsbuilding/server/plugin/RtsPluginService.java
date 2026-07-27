package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative plugin inventory and capability service.
 *
 * <p>All install paths, uninstall paths, feature checks, and numeric plugin
 * limits enter here. UI, packets, and item classes are adapters only. This
 * keeps the survival-balance system from becoming another scattered skill tree.
 */
public final class RtsPluginService {
    private RtsPluginService() {
    }

    public static boolean canUse(EntityPlayerMP player, RtsFeature feature) {
        if (!RtsProgressionManager.isEnabled()) {
            return true;
        }
        if (player == null || feature == null) {
            return false;
        }
        for (RtsPluginTeamService.EffectivePlugin effective : RtsPluginTeamService.effectivePlugins(player)) {
            RtsPluginDefinition definition = RtsPluginRegistry.byId(effective.plugin().pluginId());
            if (definition != null && definition.enables(feature)) {
                return true;
            }
        }
        return false;
    }

    public static int actionRadius(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled()) {
            return Config.maxActionRadiusBlocks();
        }
        int radius = 0;
        for (RtsPluginTeamService.EffectivePlugin effective : RtsPluginTeamService.effectivePlugins(player)) {
            RtsPluginDefinition definition = RtsPluginRegistry.byId(effective.plugin().pluginId());
            if (definition != null && definition.radiusBlocks() > 0) {
                radius = Math.max(radius, definition.radiusBlocks());
            }
        }
        int fallback = hasEffectivePlugin(player, BuiltInRtsPluginCatalog.RTS_CONTROL_CORE) ? 16 : 1;
        return Math.max(1, Math.min(Config.maxActionRadiusBlocks(), Math.max(radius, fallback)));
    }

    /**
     * 返回玩家所在队伍当前生效的范围挖掘采掘等级。
     *
     * <p>返回 {@code null} 表示生存平衡已开启，但队伍没有安装采掘等级插件；
     * 关闭生存平衡时则直接视为无限制，不要求插件物品。
     */
    public static RangeMiningHarvestTier rangeMiningHarvestTier(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled()) {
            return RangeMiningHarvestTier.UNLIMITED;
        }
        if (player == null) {
            return null;
        }
        RangeMiningHarvestTier highest = null;
        for (RtsPluginTeamService.EffectivePlugin effective : RtsPluginTeamService.effectivePlugins(player)) {
            RtsPluginDefinition definition = RtsPluginRegistry.byId(effective.plugin().pluginId());
            if (definition == null || definition.harvestTier() == null) {
                continue;
            }
            if (highest == null
                    || definition.harvestTier().maxRequiredLevel() > highest.maxRequiredLevel()) {
                highest = definition.harvestTier();
            }
        }
        return highest;
    }

    public static boolean canBypassHomeRadius(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled()) {
            return true;
        }
        return hasEffectivePlugin(player, BuiltInRtsPluginCatalog.FIELD_DEPLOYMENT_PLUGIN);
    }

    public static boolean installFromInventorySlot(EntityPlayerMP player, int inventorySlot) {
        if (player == null || inventorySlot < 0 || inventorySlot >= player.inventory.mainInventory.size()) {
            return fail(player, "message.rtsbuilding.plugin.invalid_slot");
        }
        ItemStack stack = player.inventory.mainInventory.get(inventorySlot);
        InstallResult result = validateInstall(player, stack);
        if (!result.success()) {
            return fail(player, result.messageKey());
        }
        ItemStack installedStack = stack.splitStack(1);
        if (stack.isEmpty()) {
            player.inventory.setInventorySlotContents(inventorySlot, ItemStack.EMPTY);
        }
        player.inventory.markDirty();
        boolean replaced = addInstalled(player, result.definition(), installedStack);
        syncInventory(player);
        success(player, replaced
                ? "message.rtsbuilding.plugin.replaced"
                : "message.rtsbuilding.plugin.installed");
        return true;
    }

    public static boolean installHeldPlugin(EntityPlayerMP player, EnumHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        ItemStack stack = player.getHeldItem(hand);
        InstallResult result = validateInstall(player, stack);
        if (!result.success()) {
            return fail(player, result.messageKey());
        }
        ItemStack installedStack = stack.splitStack(1);
        if (stack.isEmpty()) {
            player.setHeldItem(hand, ItemStack.EMPTY);
        }
        player.inventory.markDirty();
        boolean replaced = addInstalled(player, result.definition(), installedStack);
        syncInventory(player);
        success(player, replaced
                ? "message.rtsbuilding.plugin.replaced"
                : "message.rtsbuilding.plugin.installed");
        return true;
    }

    public static boolean uninstall(EntityPlayerMP player, ResourceLocation pluginId) {
        if (player == null || pluginId == null) {
            return false;
        }
        List<RtsPluginTeamService.StoredPlugin> installed = RtsPluginTeamService.installedPlugins(player);
        for (int i = 0; i < installed.size(); i++) {
            RtsPluginTeamService.StoredPlugin entry = installed.get(i);
            if (!pluginId.equals(entry.plugin().pluginId())) {
                continue;
            }
            if (!entry.isOwnedBy(player)) {
                return fail(player, "message.rtsbuilding.plugin.not_yours");
            }
            ItemStack returning = RtsInstalledPlugin.copyOne(entry.plugin().stack());
            if (!canFitWholeStack(player, returning)) {
                return fail(player, "message.rtsbuilding.plugin.inventory_full");
            }
            if (!player.inventory.addItemStackToInventory(returning)) {
                return fail(player, "message.rtsbuilding.plugin.inventory_full");
            }
            installed.remove(i);
            player.inventory.markDirty();
            RtsPluginTeamService.saveInstalledPlugins(player, installed);
            syncRelatedPlayers(player);
            syncInventory(player);
            success(player, "message.rtsbuilding.plugin.uninstalled");
            return true;
        }
        return fail(player, "message.rtsbuilding.plugin.not_installed");
    }

    public static void syncToPlayer(EntityPlayerMP player) {
        if (player != null) RtsEffectAccumulator.INSTANCE.markPluginState(player.getUniqueID());
    }

    /** 仅由 Tick 末 Effect Committer 调用，普通业务入口只登记最新完整快照。 */
    public static void syncToPlayerNow(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        List<RtsPluginTeamService.EffectivePlugin> effectivePlugins = RtsPluginTeamService.effectivePlugins(player);
        List<String> pluginIds = new ArrayList<>(effectivePlugins.size());
        List<String> families = new ArrayList<>(effectivePlugins.size());
        List<Integer> radii = new ArrayList<>(effectivePlugins.size());
        List<Boolean> fieldDeployment = new ArrayList<>(effectivePlugins.size());
        List<Boolean> personal = new ArrayList<>(effectivePlugins.size());
        List<String> ownerNames = new ArrayList<>(effectivePlugins.size());
        List<ItemStack> stacks = new ArrayList<>(effectivePlugins.size());
        for (RtsPluginTeamService.EffectivePlugin effective : effectivePlugins) {
            RtsInstalledPlugin entry = effective.plugin();
            RtsPluginDefinition definition = RtsPluginRegistry.byId(entry.pluginId());
            if (definition == null) {
                continue;
            }
            pluginIds.add(definition.id().toString());
            families.add(definition.family().name());
            radii.add(definition.radiusBlocks());
            fieldDeployment.add(definition.fieldDeployment());
            personal.add(effective.personal());
            ownerNames.add(effective.ownerName());
            stacks.add(RtsInstalledPlugin.copyOne(entry.stack()));
        }
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsPluginStatePayload(
                pluginIds, families, radii, fieldDeployment, personal, ownerNames, stacks,
                RtsPluginTeamService.teamLabel(player)));
    }

    public static void syncRelatedPlayers(EntityPlayerMP player) {
        for (EntityPlayerMP relatedPlayer : RtsPluginTeamService.relatedPlayers(player)) {
            syncToPlayer(relatedPlayer);
            RtsProgressionManager.syncToPlayer(relatedPlayer);
        }
    }

    public static void migrateLegacySkillTree(EntityPlayerMP player) {
        List<RtsPluginDefinition> migrated = RtsLegacySkillTreeMigration.migrate(player);
        if (migrated.isEmpty()) {
            return;
        }
        player.sendStatusMessage(RtsLegacySkillTreeMigration.migrationMessage(migrated), false);
        syncRelatedPlayers(player);
    }

    public static List<RtsInstalledPlugin> installedPlugins(EntityPlayerMP player) {
        if (player == null) {
            return java.util.Collections.emptyList();
        }
        List<RtsPluginTeamService.StoredPlugin> stored = RtsPluginTeamService.installedPlugins(player);
        List<RtsInstalledPlugin> installed = new ArrayList<>(stored.size());
        for (RtsPluginTeamService.StoredPlugin plugin : stored) {
            installed.add(plugin.plugin());
        }
        return installed;
    }

    public static boolean isPluginItem(ItemStack stack) {
        return RtsPluginRegistry.isPluginItem(stack);
    }

    private static InstallResult validateInstall(EntityPlayerMP player, ItemStack stack) {
        RtsPluginDefinition definition = RtsPluginRegistry.byItem(stack);
        if (definition == null) {
            return InstallResult.fail("message.rtsbuilding.plugin.not_plugin");
        }
        List<RtsPluginTeamService.StoredPlugin> installed = RtsPluginTeamService.installedPlugins(player);
        for (RtsPluginTeamService.StoredPlugin entry : installed) {
            RtsPluginDefinition existing = RtsPluginRegistry.byId(entry.plugin().pluginId());
            if (existing == null) {
                continue;
            }
            if (existing.id().equals(definition.id())) {
                return InstallResult.fail("message.rtsbuilding.plugin.already_installed");
            }
        }
        return InstallResult.success(definition);
    }

    /**
     * 安装插件，并在同一次保存中替换同一互斥家族的旧插件。
     *
     * <p>旧插件优先退回安装者背包；背包确实放不下时掉在玩家脚边，绝不静默吞掉。
     */
    private static boolean addInstalled(
            EntityPlayerMP player, RtsPluginDefinition definition, ItemStack installedStack) {
        List<RtsPluginTeamService.StoredPlugin> installed = RtsPluginTeamService.installedPlugins(player);
        boolean replaced = false;
        if (definition.family().mutuallyExclusive()) {
            for (int i = installed.size() - 1; i >= 0; i--) {
                RtsPluginTeamService.StoredPlugin entry = installed.get(i);
                RtsPluginDefinition existing = RtsPluginRegistry.byId(entry.plugin().pluginId());
                if (existing == null || existing.family() != definition.family()) {
                    continue;
                }
                installed.remove(i);
                returnReplacedPlugin(player, entry.plugin().stack());
                replaced = true;
            }
        }
        installed.add(new RtsPluginTeamService.StoredPlugin(
                new RtsInstalledPlugin(definition.id(), installedStack, player.world.getTotalWorldTime()),
                player.getUniqueID(),
                player.getGameProfile().getName()));
        RtsPluginTeamService.saveInstalledPlugins(player, installed);
        syncRelatedPlayers(player);
        return replaced;
    }

    private static void returnReplacedPlugin(EntityPlayerMP player, ItemStack installedStack) {
        ItemStack returning = installedStack == null
                ? ItemStack.EMPTY
                : RtsInstalledPlugin.copyOne(installedStack);
        if (returning.isEmpty()) {
            return;
        }
        if (!player.inventory.addItemStackToInventory(returning)) {
            player.dropItem(returning, false);
        }
        player.inventory.markDirty();
    }

    /** 卸载是事务式操作；先证明整栈可放入，避免 InventoryPlayer 的部分插入制造复制。 */
    private static boolean canFitWholeStack(EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        int remaining = stack.getCount();
        for (ItemStack slot : player.inventory.mainInventory) {
            if (slot.isEmpty()) return true;
            if (ItemStack.areItemsEqual(slot, stack) && ItemStack.areItemStackTagsEqual(slot, stack)) {
                remaining -= Math.max(0, Math.min(slot.getMaxStackSize(), player.inventory.getInventoryStackLimit())
                        - slot.getCount());
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    private static boolean hasPlugin(EntityPlayerMP player, ResourceLocation pluginId) {
        if (player == null || pluginId == null) {
            return false;
        }
        for (RtsInstalledPlugin entry : installedPlugins(player)) {
            if (pluginId.equals(entry.pluginId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEffectivePlugin(EntityPlayerMP player, ResourceLocation pluginId) {
        if (player == null || pluginId == null) {
            return false;
        }
        for (RtsPluginTeamService.EffectivePlugin effective : RtsPluginTeamService.effectivePlugins(player)) {
            if (pluginId.equals(effective.plugin().pluginId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean fail(EntityPlayerMP player, String key) {
        if (player != null && key != null && !key.isEmpty()) {
            player.sendStatusMessage(new TextComponentTranslation(key), true);
        }
        return false;
    }

    /**
     * 插件装卸会直接改玩家背包；立即同步槽位，避免客户端继续把已安装插件
     * 当作快捷栏里的挖掘工具发送给服务端。
     */
    private static void syncInventory(EntityPlayerMP player) {
        player.inventoryContainer.detectAndSendChanges();
        if (player.openContainer != player.inventoryContainer) {
            player.openContainer.detectAndSendChanges();
        }
        player.getServer().getPlayerList().syncPlayerInventory(player);
    }

    private static void success(EntityPlayerMP player, String key) {
        if (player != null && key != null && !key.isEmpty()) {
            player.sendStatusMessage(new TextComponentTranslation(key), true);
        }
    }

    private static final class InstallResult {
        private final boolean success;
        private final RtsPluginDefinition definition;
        private final String messageKey;

        private InstallResult(boolean success, RtsPluginDefinition definition, String messageKey) {
            this.success = success;
            this.definition = definition;
            this.messageKey = messageKey;
        }

        boolean success() { return success; }
        RtsPluginDefinition definition() { return definition; }
        String messageKey() { return messageKey; }

        static InstallResult success(RtsPluginDefinition definition) {
            return new InstallResult(true, definition, "");
        }

        static InstallResult fail(String messageKey) {
            return new InstallResult(false, null, messageKey);
        }
    }
}
