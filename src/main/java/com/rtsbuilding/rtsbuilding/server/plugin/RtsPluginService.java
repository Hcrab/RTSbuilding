package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

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

    public static boolean canUse(ServerPlayer player, RtsFeature feature) {
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

    public static int actionRadius(ServerPlayer player) {
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
    public static RangeMiningHarvestTier rangeMiningHarvestTier(ServerPlayer player) {
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

    public static boolean canBypassHomeRadius(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled()) {
            return true;
        }
        return hasEffectivePlugin(player, BuiltInRtsPluginCatalog.FIELD_DEPLOYMENT_PLUGIN);
    }

    public static boolean installFromInventorySlot(ServerPlayer player, int inventorySlot) {
        if (player == null || inventorySlot < 0 || inventorySlot >= player.getInventory().items.size()) {
            return fail(player, "message.rtsbuilding.plugin.invalid_slot");
        }
        ItemStack stack = player.getInventory().items.get(inventorySlot);
        InstallResult result = validateInstall(player, stack);
        if (!result.success()) {
            return fail(player, result.messageKey());
        }
        ItemStack installedStack = stack.split(1);
        if (stack.isEmpty()) {
            player.getInventory().setItem(inventorySlot, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        boolean replaced = addInstalled(player, result.definition(), installedStack);
        syncInventory(player);
        success(player, replaced
                ? "message.rtsbuilding.plugin.replaced"
                : "message.rtsbuilding.plugin.installed");
        return true;
    }

    public static boolean installHeldPlugin(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        InstallResult result = validateInstall(player, stack);
        if (!result.success()) {
            return fail(player, result.messageKey());
        }
        ItemStack installedStack = stack.split(1);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        boolean replaced = addInstalled(player, result.definition(), installedStack);
        syncInventory(player);
        success(player, replaced
                ? "message.rtsbuilding.plugin.replaced"
                : "message.rtsbuilding.plugin.installed");
        return true;
    }

    public static boolean uninstall(ServerPlayer player, ResourceLocation pluginId) {
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
            ItemStack returning = entry.plugin().stack().copyWithCount(1);
            if (!player.getInventory().add(returning)) {
                return fail(player, "message.rtsbuilding.plugin.inventory_full");
            }
            installed.remove(i);
            player.getInventory().setChanged();
            RtsPluginTeamService.saveInstalledPlugins(player, installed);
            syncRelatedPlayers(player);
            syncInventory(player);
            success(player, "message.rtsbuilding.plugin.uninstalled");
            return true;
        }
        return fail(player, "message.rtsbuilding.plugin.not_installed");
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player != null) RtsEffectAccumulator.INSTANCE.markPluginState(player.getUUID());
    }

    /** 仅由 Tick 末 Effect Committer 调用，普通业务入口只登记最新完整快照。 */
    public static void syncToPlayerNow(ServerPlayer player) {
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
            stacks.add(entry.stack().copyWithCount(1));
        }
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsPluginStatePayload(
                pluginIds, families, radii, fieldDeployment, personal, ownerNames, stacks,
                RtsPluginTeamService.teamLabel(player)));
    }

    public static void syncRelatedPlayers(ServerPlayer player) {
        for (ServerPlayer relatedPlayer : RtsPluginTeamService.relatedPlayers(player)) {
            syncToPlayer(relatedPlayer);
            RtsProgressionManager.syncToPlayer(relatedPlayer);
        }
    }

    public static void migrateLegacySkillTree(ServerPlayer player) {
        List<RtsPluginDefinition> migrated = RtsLegacySkillTreeMigration.migrate(player);
        if (migrated.isEmpty()) {
            return;
        }
        player.displayClientMessage(RtsLegacySkillTreeMigration.migrationMessage(migrated), false);
        syncRelatedPlayers(player);
    }

    public static List<RtsInstalledPlugin> installedPlugins(ServerPlayer player) {
        if (player == null) {
            return List.of();
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

    private static InstallResult validateInstall(ServerPlayer player, ItemStack stack) {
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
            ServerPlayer player, RtsPluginDefinition definition, ItemStack installedStack) {
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
                new RtsInstalledPlugin(definition.id(), installedStack, player.level().getGameTime()),
                player.getUUID(),
                player.getGameProfile().getName()));
        RtsPluginTeamService.saveInstalledPlugins(player, installed);
        syncRelatedPlayers(player);
        return replaced;
    }

    private static void returnReplacedPlugin(ServerPlayer player, ItemStack installedStack) {
        ItemStack returning = installedStack == null
                ? ItemStack.EMPTY
                : installedStack.copyWithCount(1);
        if (returning.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(returning)) {
            player.drop(returning, false);
        }
        player.getInventory().setChanged();
    }

    private static boolean hasPlugin(ServerPlayer player, ResourceLocation pluginId) {
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

    private static boolean hasEffectivePlugin(ServerPlayer player, ResourceLocation pluginId) {
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

    private static boolean fail(ServerPlayer player, String key) {
        if (player != null && key != null && !key.isBlank()) {
            player.displayClientMessage(Component.translatable(key), true);
        }
        return false;
    }

    /**
     * 插件装卸会直接改玩家背包；立即同步槽位，避免客户端继续把已安装插件
     * 当作快捷栏里的挖掘工具发送给服务端。
     */
    private static void syncInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void success(ServerPlayer player, String key) {
        if (player != null && key != null && !key.isBlank()) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private record InstallResult(boolean success, RtsPluginDefinition definition, String messageKey) {
        static InstallResult success(RtsPluginDefinition definition) {
            return new InstallResult(true, definition, "");
        }

        static InstallResult fail(String messageKey) {
            return new InstallResult(false, null, messageKey);
        }
    }
}
