package com.rtsbuilding.rtsbuilding.network.handler;

import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.item.RtsTerminalItem;
import com.rtsbuilding.rtsbuilding.core.network.ActionType;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.network.message.C2SAction;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsCarriedSyncPayload;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsBlueprintJobService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.RtsResumeScanResult;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public final class ServerActionHandler {
    private static final Logger LOG = LoggerFactory.getLogger("RtsAction");

    private ServerActionHandler() {}

    public static void handle(C2SAction payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            try { dispatch(payload, p); }
            catch (Exception e) { LOG.error("Error handling {} from {}: {}", payload.actionType(), p.getName().getString(), e.getMessage()); }
        });
    }

    private static void dispatch(C2SAction msg, ServerPlayer p) {
        var t = msg.params();
        if (t == null) { LOG.debug("Null params for {}", msg.actionType()); return; }
        switch (msg.actionType()) {
            case SET_MODE -> {
                int id = t.getByte("mode") & 0xFF;
                var modes = BuilderMode.values();
                if (id >= 0 && id < modes.length) RtsServer.get().binding().setMode(p, modes[id]);
            }
            case TOGGLE_CAMERA -> {
                boolean enable = t.getBoolean("startAtPlayerHead");
                String terminalUuid = null;
                if (enable) {
                    // Turn-on consumes terminal energy — the server is authoritative.
                    ItemStack stack = p.getMainHandItem();
                    IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                    if (energy == null) {
                        stack = p.getOffhandItem();
                        energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                    }
                    if (energy != null) {
                        if (energy.getEnergyStored() < RtsTerminalItem.ENERGY_PER_USE) {
                            p.displayClientMessage(Component.translatable("message.rtsbuilding.terminal_no_energy"), true);
                            return;
                        }
                        energy.extractEnergy(RtsTerminalItem.ENERGY_PER_USE, false);
                        // 记录“开启该模式的那把终端”，RTS 模式下禁止对它拿去/启用
                        terminalUuid = stack.get(RtsItems.TERMINAL_UUID.get());
                    }
                }
                RtsCameraManager.toggle(p, enable, terminalUuid);
            }
            case SET_FUNNEL -> RtsServer.get().binding().setFunnelEnabled(p, t.getBoolean("enabled"));
            case SET_AUTO_STORE -> RtsServer.get().binding().setAutoStoreMinedDrops(p, t.getBoolean("enabled"));
            case SET_BD_NETWORK -> RtsServer.get().binding().setBdNetworkEnabled(p, t.getBoolean("enabled"));
            case LINK_STORAGE -> RtsServer.get().binding().linkStorage(p, BlockPos.of(t.getLong("pos")), t.getByte("allowStore"));
            case UNLINK_STORAGE -> RtsServer.get().binding().unlinkStorage(p, BlockPos.of(t.getLong("pos")));
            case UPDATE_LINKED_STORAGE -> RtsServer.get().binding().updateLinkedStorageSettings(p, BlockPos.of(t.getLong("pos")), t.getByte("extractOnly"), t.getInt("priority"));
            case FILL_INVENTORY -> RtsServer.get().transfer().fillPlayerInventoryFromLinked(p);
            case CLOSE_REMOTE_MENU -> RtsServer.get().binding().closeRemoteMenu(p);
            case STORE_HOTBAR_SLOT -> RtsServer.get().binding().storeHotbarSlot(p, (byte) (t.getByte("slot") & 0xFF));
            case REQUEST_PAGE -> {
                var sort = com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort.byId(t.getByte("sort"));
                RtsServer.get().page().requestPage(p, t.getInt("page"), t.getString("search"), t.getString("category"), sort, t.getBoolean("ascending"), t.getInt("pageSize"), true, new ArrayList<>());
            }
            case CRAFT_RECIPE -> RtsServer.get().crafting().craftRecipeToLinked(p, t.getString("recipeId"), t.getInt("count"));
            case REQUEST_CRAFTABLES -> RtsServer.get().crafting().requestCraftables(p, t.getString("search"), t.getBoolean("showUnavailable"), t.getInt("offset"), t.getInt("limit"), true, new ArrayList<>());
            case OPEN_CRAFT_TERMINAL -> RtsServer.get().crafting().openCraftTerminal(p);
            case PLACE_BLOCK -> {
                if (!isBuildMode(p)) return;
                Direction face = Direction.from3DDataValue(t.getByte("face"));
                RtsServer.get().placement().placeSelected(p, BlockPos.of(t.getLong("pos")), face, t.getDouble("hitX"), t.getDouble("hitY"), t.getDouble("hitZ"), t.getByte("rotateSteps"), t.getBoolean("forcePlace"), t.getBoolean("skipIfOccupied"), t.getString("itemId"), net.minecraft.world.item.ItemStack.EMPTY, t.getDouble("rayOriginX"), t.getDouble("rayOriginY"), t.getDouble("rayOriginZ"), t.getDouble("rayDirX"), t.getDouble("rayDirY"), t.getDouble("rayDirZ"), t.getBoolean("quickBuild"), false);
            }
            case PLACE_FLUID -> {
                if (!isBuildMode(p)) return;
                Direction face = Direction.from3DDataValue(t.getByte("face"));
                RtsServer.get().fluid().placeFluid(p, BlockPos.of(t.getLong("pos")), face, t.getDouble("hitX"), t.getDouble("hitY"), t.getDouble("hitZ"), t.getBoolean("forcePlace"), t.getString("fluidId"), t.getDouble("rayOriginX"), t.getDouble("rayOriginY"), t.getDouble("rayOriginZ"), t.getDouble("rayDirX"), t.getDouble("rayDirY"), t.getDouble("rayDirZ"));
            }
            case ROTATE_BLOCK -> RtsServer.get().placement().rotateBlock(p, BlockPos.of(t.getLong("pos")));
            case STORE_FLUID -> RtsServer.get().fluid().storeFluidFromContainer(p, t.getByte("sourceType"), t.getByte("toolSlot"), t.getString("itemId"));
            case SUBMIT_PENDING -> RtsServer.get().placement().submitPendingPlacement(p);
            case MINE_BLOCK -> {
                if (!isBuildMode(p)) return;
                Direction face = Direction.from3DDataValue(t.getByte("face"));
                RtsServer.get().mining().mine(p, BlockPos.of(t.getLong("pos")), face, t.getBoolean("start"), t.getByte("toolSlot"), t.getString("toolItemId"), net.minecraft.world.item.ItemStack.EMPTY, t.getBoolean("allowPlacedBlockRecovery"), t.getBoolean("toolProtectionEnabled"));
            }
            case ULTIMINE -> {
                if (!isBuildMode(p)) return;
                Direction face = Direction.from3DDataValue(t.getByte("face"));
                RtsServer.get().mining().startUltimine(p, BlockPos.of(t.getLong("pos")), face, t.getByte("toolSlot"), t.getString("toolItemId"), net.minecraft.world.item.ItemStack.EMPTY, t.getShort("limit") & 0xFFFF, t.getByte("mode"), t.getBoolean("toolProtectionEnabled"));
            }
            case AREA_MINE -> {
                if (!isBuildMode(p)) return;
                RtsServer.get().mining().areaMine(p, t.getInt("minX"), t.getInt("maxX"), t.getInt("minY"), t.getInt("maxY"), t.getInt("minZ"), t.getInt("maxZ"), t.getByte("toolSlot"), t.getString("toolItemId"), net.minecraft.world.item.ItemStack.EMPTY, t.getByte("shapeType"), t.getByte("fillType"), t.getBoolean("toolProtectionEnabled"));
            }
            case AREA_DESTROY -> {
                if (!isBuildMode(p)) return;
                var list = t.getList("positions", net.minecraft.nbt.Tag.TAG_LONG);
                var positions = new ArrayList<BlockPos>();
                for (int i = 0; i < list.size(); i++) positions.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
                RtsServer.get().mining().areaDestroy(p, positions, t.getByte("toolSlot"), t.getString("toolItemId"), net.minecraft.world.item.ItemStack.EMPTY, t.getBoolean("toolProtectionEnabled"));
            }
            case BREAK -> {
                if (!isBuildMode(p)) return;
                Direction face = Direction.from3DDataValue(t.getByte("face"));
                RtsPlacedRecoveryService.breakPlaced(p, BlockPos.of(t.getLong("pos")), face, t.getBoolean("allowAdjacentFallback"));
            }
            case INTERACT_BLOCK -> {
                Direction face = Direction.from3DDataValue(t.getByte("face"));
                RtsServer.get().interaction().interactTarget(p, t.getInt("entityId"), BlockPos.of(t.getLong("clickedPos")), face, t.getDouble("hitX"), t.getDouble("hitY"), t.getDouble("hitZ"), t.getByte("sourceType"), t.getByte("toolSlot"), t.getString("itemId"), t.getDouble("rayOriginX"), t.getDouble("rayOriginY"), t.getDouble("rayOriginZ"), t.getDouble("rayDirX"), t.getDouble("rayDirY"), t.getDouble("rayDirZ"));
            }
            case QUICK_DROP -> RtsServer.get().transfer().quickDropLinkedItem(p, t.getString("itemId"), (byte) t.getInt("amount"), t.getDouble("dropX"), t.getDouble("dropY"), t.getDouble("dropZ"));
            case LINKED_PICKUP -> {
                // Pick linked-storage items into the open container menu carried slot.
                var prototype = net.minecraft.world.item.ItemStack.parseOptional(p.registryAccess(), t.getCompound("prototype"));
                if (prototype.isEmpty()) return;
                RtsServer.get().transfer().pickupLinkedToCarried(p, prototype, t.getInt("amount"), t.getBoolean("fromInventory"));
                // Client carried field is not auto-synced; mirror the authoritative server state.
                PacketDistributor.sendToPlayer(p, new S2CRtsCarriedSyncPayload(p.containerMenu.getCarried()));
            }
            case RETURN_CARRIED -> {
                // Return the carried stack (or part of it) back to the linked storage.
                RtsServer.get().transfer().returnCarriedToLinked(p, t.getString("itemId"), t.getInt("amount"));
                PacketDistributor.sendToPlayer(p, new S2CRtsCarriedSyncPayload(p.containerMenu.getCarried()));
            }
            case LINKED_QUICK_MOVE -> {
                // Shift-style quick move from linked storage straight into the open menu.
                var quickPrototype = net.minecraft.world.item.ItemStack.parseOptional(p.registryAccess(), t.getCompound("prototype"));
                if (quickPrototype.isEmpty()) return;
                RtsServer.get().transfer().quickMoveLinkedItem(p, quickPrototype, t.getBoolean("fromInventory"));
            }
            case IMPORT_MENU_SLOT -> {
                // Shift-click a slot in the open container menu: import that slot's item into linked storage.
                RtsServer.get().transfer().importMenuSlotToLinked(p, t.getInt("slot"));
            }
            case UNDO -> { if (RtsCameraManager.isActive(p)) ServerHistoryManager.executeUndo(p); }
            case CAMERA_POSE -> RtsCameraManager.updateCameraPose(p,
                    t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                    t.getFloat("yaw"), t.getFloat("pitch"));
            case PAUSE_WORKFLOW -> {
                int entryId = t.getInt("entryId");
                var engine = RtsWorkflowEngine.getInstance();
                var status = engine.getProgress(p, entryId);
                if (!status.isActive()) return;
                engine.from(p, entryId).ifPresent(token -> {
                    if (token.isPaused()) { token.unpause(); p.displayClientMessage(Component.literal("§7[工作流] §a▶ 已恢复"), true); }
                    else if (token.isSuspended()) { token.resume(); p.displayClientMessage(Component.literal("§7[工作流] §a▶ 已恢复"), true); }
                    else { token.pause(); p.displayClientMessage(Component.literal("§7[工作流] §e⏸ 已暂停"), true); }
                });
            }
            case DELETE_WORKFLOW -> RtsWorkflowEngine.getInstance().deleteWorkflow(p, t.getInt("entryId"));
            case REQUEST_PLUGINS -> LOG.debug("REQUEST_PLUGINS not yet migrated");
            case SET_PROGRESSION -> LOG.debug("SET_PROGRESSION not yet migrated");
            case SET_HOME -> LOG.debug("SET_HOME not yet migrated");
            case BEGIN_HOME_SELECTION -> LOG.debug("BEGIN_HOME_SELECTION not yet migrated");
            case REQUEST_PROGRESSION -> LOG.debug("REQUEST_PROGRESSION not yet migrated");
            case PATHFIND -> RtsServer.get().pathfinding().goTo(p, BlockPos.of(t.getLong("target")));
            default -> LOG.debug("Unhandled: {} from {}", msg.actionType(), p.getName().getString());
        }
    }

    private static boolean isBuildMode(ServerPlayer p) {
        if (!RtsCameraManager.isActive(p)) return true;
        var session = RtsServer.get().session().getIfPresent(p);
        return session != null && session.mode == BuilderMode.BUILD;
    }
}
