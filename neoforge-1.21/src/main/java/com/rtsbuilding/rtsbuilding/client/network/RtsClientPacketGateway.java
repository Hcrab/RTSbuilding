package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.core.network.ActionType;
import com.rtsbuilding.rtsbuilding.network.message.C2SAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class RtsClientPacketGateway {
    private RtsClientPacketGateway() {}

    private static C2SAction act(ActionType type, CompoundTag t) { return new C2SAction(type, null, t); }
    private static CompoundTag tag() { return new CompoundTag(); }

    public static void sendSetMode(BuilderMode mode) {
        var t = tag(); t.putByte("mode", (byte) mode.ordinal());
        PacketDistributor.sendToServer(act(ActionType.SET_MODE, t));
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        var t = tag(); t.putBoolean("startAtPlayerHead", startAtPlayerHead);
        PacketDistributor.sendToServer(act(ActionType.TOGGLE_CAMERA, t));
    }

    public static void sendSetFunnelEnabled(boolean enabled) {
        var t = tag(); t.putBoolean("enabled", enabled);
        PacketDistributor.sendToServer(act(ActionType.SET_FUNNEL, t));
    }

    public static void sendSetAutoStoreMinedDrops(boolean enabled) {
        var t = tag(); t.putBoolean("enabled", enabled);
        PacketDistributor.sendToServer(act(ActionType.SET_AUTO_STORE, t));
    }

    public static void sendSetBdNetwork(boolean enabled) {
        var t = tag(); t.putBoolean("enabled", enabled);
        PacketDistributor.sendToServer(act(ActionType.SET_BD_NETWORK, t));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putBoolean("allowStore", allowStore);
        PacketDistributor.sendToServer(act(ActionType.LINK_STORAGE, t));
    }

    public static void sendUpdateLinkedStorage(BlockPos pos, boolean extractOnly, int priority) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putBoolean("extractOnly", extractOnly); t.putInt("priority", priority);
        PacketDistributor.sendToServer(act(ActionType.UPDATE_LINKED_STORAGE, t));
    }

    public static void sendUnlinkStorage(BlockPos pos) {
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.UNLINK_STORAGE, t));
    }

    public static void sendRequestStoragePage(int page, String search, String category,
                                               com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort sort,
                                               boolean ascending, int pageSize) {
        var t = tag(); t.putInt("page", page); t.putString("search", search == null ? "" : search);
        t.putString("category", category == null ? "" : category);
        t.putByte("sort", (byte) sort.ordinal()); t.putBoolean("ascending", ascending); t.putInt("pageSize", pageSize);
        PacketDistributor.sendToServer(act(ActionType.REQUEST_PAGE, t));
    }

    public static void sendRequestCraftables(String search, boolean showUnavailable, int offset, int limit) {
        var t = tag(); t.putString("search", search == null ? "" : search);
        t.putBoolean("showUnavailable", showUnavailable); t.putInt("offset", offset); t.putInt("limit", limit);
        PacketDistributor.sendToServer(act(ActionType.REQUEST_CRAFTABLES, t));
    }

    public static void sendCraftRecipe(String recipeId, int count) {
        var t = tag(); t.putString("recipeId", recipeId == null ? "" : recipeId); t.putInt("count", count);
        PacketDistributor.sendToServer(act(ActionType.CRAFT_RECIPE, t));
    }

    public static void sendOpenCraftTerminal() {
        PacketDistributor.sendToServer(act(ActionType.OPEN_CRAFT_TERMINAL, tag()));
    }

    public static void sendCloseRemoteMenu() {
        PacketDistributor.sendToServer(act(ActionType.CLOSE_REMOTE_MENU, tag()));
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied,
                                  String itemId, ItemStack itemPrototype, int rotateSteps,
                                  Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype, rotateSteps, rayOrigin, rayDir, false);
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied,
                                  String itemId, ItemStack itemPrototype, int rotateSteps,
                                  Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild) {
        var t = tag(); t.putLong("pos", hit.getBlockPos().asLong());
        t.putByte("face", (byte) hit.getDirection().get3DDataValue());
        t.putDouble("hitX", hit.getLocation().x); t.putDouble("hitY", hit.getLocation().y); t.putDouble("hitZ", hit.getLocation().z);
        t.putByte("rotateSteps", (byte) rotateSteps); t.putBoolean("forcePlace", forcePlace);
        t.putBoolean("skipIfOccupied", skipIfOccupied);
        t.putString("itemId", itemId == null ? "" : itemId);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        t.putBoolean("quickBuild", quickBuild);
        PacketDistributor.sendToServer(act(ActionType.PLACE_BLOCK, t));
    }

    public static void sendPlaceFluid(BlockHitResult hit, boolean forcePlace, String fluidId, Vec3 rayOrigin, Vec3 rayDir) {
        var t = tag(); t.putLong("pos", hit.getBlockPos().asLong());
        t.putByte("face", (byte) hit.getDirection().get3DDataValue());
        t.putDouble("hitX", hit.getLocation().x); t.putDouble("hitY", hit.getLocation().y); t.putDouble("hitZ", hit.getLocation().z);
        t.putBoolean("forcePlace", forcePlace); t.putString("fluidId", fluidId == null ? "" : fluidId);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.PLACE_FLUID, t));
    }

    public static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
                                      boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face);
        t.putBoolean("start", true); t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putBoolean("allowPlacedBlockRecovery", allowPlacedBlockRecovery);
        t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.MINE_BLOCK, t));
    }

    public static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face);
        t.putBoolean("start", false); t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        PacketDistributor.sendToServer(act(ActionType.MINE_BLOCK, t));
    }

    public static void sendUltimineStart(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                                          String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face);
        t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putShort("limit", (short) Mth.clamp(limit, 1, 256)); t.putByte("mode", mode);
        t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.ULTIMINE, t));
    }

    public static void sendRotateBlock(BlockPos pos) {
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.ROTATE_BLOCK, t));
    }

    public static void sendRequestPlugins() {
        PacketDistributor.sendToServer(act(ActionType.REQUEST_PLUGINS, tag()));
    }

    public static void sendRequestProgressionState() {
        PacketDistributor.sendToServer(act(ActionType.REQUEST_PROGRESSION, tag()));
    }

    public static void sendSetSurvivalProgression(boolean enabled) {
        var t = tag(); t.putBoolean("enabled", enabled);
        PacketDistributor.sendToServer(act(ActionType.SET_PROGRESSION, t));
    }

    public static void sendSetHome(BlockPos pos) {
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.SET_HOME, t));
    }

    public static void sendBeginHomeSelection() {
        PacketDistributor.sendToServer(act(ActionType.BEGIN_HOME_SELECTION, tag()));
    }

    public static void sendPathfindingGoTo(BlockPos target) {
        var t = tag(); t.putLong("target", target.asLong());
        PacketDistributor.sendToServer(act(ActionType.PATHFIND, t));
    }

    public static void sendUndo() {
        PacketDistributor.sendToServer(act(ActionType.UNDO, tag()));
    }

    public static void sendInteractEntityEmptyHand(int entityId, Vec3 hitLocation,
                                                    @javax.annotation.Nullable BlockHitResult blockHit,
                                                    Vec3 rayOrigin, Vec3 rayDir) {
        BlockPos clickedPos; byte face;
        if (blockHit != null) { clickedPos = blockHit.getBlockPos(); face = (byte) blockHit.getDirection().get3DDataValue(); }
        else { clickedPos = BlockPos.containing(hitLocation); face = 1; }
        var t = tag(); t.putInt("entityId", entityId); t.putLong("clickedPos", clickedPos.asLong());
        t.putByte("face", face); t.putDouble("hitX", hitLocation.x); t.putDouble("hitY", hitLocation.y); t.putDouble("hitZ", hitLocation.z);
        t.putByte("sourceType", (byte) 0); t.putByte("toolSlot", (byte) 0);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.INTERACT_BLOCK, t));
    }

    public static void sendInteractEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        var t = tag(); t.putInt("entityId", entityId); t.putLong("clickedPos", BlockPos.containing(hitLocation).asLong());
        t.putByte("face", (byte) 1); t.putDouble("hitX", hitLocation.x); t.putDouble("hitY", hitLocation.y); t.putDouble("hitZ", hitLocation.z);
        t.putByte("sourceType", (byte) 1); t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.INTERACT_BLOCK, t));
    }

    public static void sendBreakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face.get3DDataValue());
        t.putBoolean("allowAdjacentFallback", allowAdjacentFallback);
        PacketDistributor.sendToServer(act(ActionType.BREAK, t));
    }

    public static void sendAreaMine(int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                     int toolSlot, String toolItemId, ItemStack toolPrototype,
                                     byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        var t = tag(); t.putInt("minX", minX); t.putInt("maxX", maxX); t.putInt("minY", minY); t.putInt("maxY", maxY);
        t.putInt("minZ", minZ); t.putInt("maxZ", maxZ);
        t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putByte("shapeType", shapeType); t.putByte("fillType", fillType); t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.AREA_MINE, t));
    }

    public static void sendAreaDestroy(List<BlockPos> positions, int toolSlot, String toolItemId,
                                        ItemStack toolPrototype, boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) return;
        var t = tag();
        var list = new ListTag();
        for (var p : positions) list.add(LongTag.valueOf(p.asLong()));
        t.put("positions", list);
        t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.AREA_DESTROY, t));
    }

    public static void sendPauseWorkflow(int entryId) {
        var t = tag(); t.putInt("entryId", entryId);
        PacketDistributor.sendToServer(act(ActionType.PAUSE_WORKFLOW, t));
    }

    public static void sendDeleteWorkflow(int entryId) {
        var t = tag(); t.putInt("entryId", entryId);
        PacketDistributor.sendToServer(act(ActionType.DELETE_WORKFLOW, t));
    }
}
