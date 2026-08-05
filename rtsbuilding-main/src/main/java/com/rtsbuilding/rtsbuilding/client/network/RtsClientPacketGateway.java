package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.core.network.ActionType;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import com.rtsbuilding.rtsbuilding.network.message.C2SAction;
import com.rtsbuilding.rtsbuilding.network.message.C2SCameraPosePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class RtsClientPacketGateway {
    private RtsClientPacketGateway() {}

    private static C2SAction act(ActionType type, CompoundTag t) { return new C2SAction(type, t); }
    private static CompoundTag tag() { return new CompoundTag(); }

    public static void sendSetMode(BuilderMode mode) {
        var t = tag(); t.putByte("mode", (byte) mode.ordinal());
        PacketDistributor.sendToServer(act(ActionType.SET_MODE, t));
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        var t = tag(); t.putBoolean("startAtPlayerHead", startAtPlayerHead);
        PacketDistributor.sendToServer(act(ActionType.TOGGLE_CAMERA, t));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        // 服务端按 byte 模式语义读取（MODE_BIDIRECTIONAL=0 / MODE_EXTRACT_ONLY=1），
        // 不能 putBoolean：true 会编码为 1，恰好撞上 MODE_EXTRACT_ONLY 导致新绑定变成仅提取。
        var t = tag(); t.putLong("pos", pos.asLong());
        t.putByte("allowStore", (byte) (allowStore ? NetworkConstants.MODE_BIDIRECTIONAL : NetworkConstants.MODE_EXTRACT_ONLY));
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

    public static void sendCloseRemoteMenu() {
        PacketDistributor.sendToServer(act(ActionType.CLOSE_REMOTE_MENU, tag()));
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied,
                                  String itemId, int rotateSteps,
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

    public static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId,
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
                                          String toolItemId, boolean toolProtectionEnabled) {
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

    public static void sendPathfindingGoTo(BlockPos target) {
        var t = tag(); t.putLong("target", target.asLong());
        PacketDistributor.sendToServer(act(ActionType.PATHFIND, t));
    }

    public static void sendUndo() {
        PacketDistributor.sendToServer(act(ActionType.UNDO, tag()));
    }

    /**
     * 实时上报客户端 RTS 相机姿态（位置 + 朝向）给服务端。
     * <p>相机移动/旋转是纯客户端计算（CameraModule 每 tick/渲染帧更新本地状态），
     * 服务端通过此方法获取相机的真实位置与朝向，供权威逻辑（如动作范围校验、实体跟随）使用。</p>
     * <p>高频路径：使用专用 payload 直接字段编解码，不走 NBT CompoundTag。</p>
     *
     * @param x     相机世界 X 坐标
     * @param y     相机世界 Y 坐标
     * @param z     相机世界 Z 坐标
     * @param yaw   偏航角（度）
     * @param pitch 俯仰角（度）
     */
    public static void sendCameraPose(double x, double y, double z, float yaw, float pitch) {
        PacketDistributor.sendToServer(new C2SCameraPosePayload(x, y, z, yaw, pitch));
    }

    public static void sendInteractEntityEmptyHand(int entityId, Vec3 hitLocation,
                                                    @javax.annotation.Nullable BlockHitResult blockHit,
                                                    Vec3 rayOrigin, Vec3 rayDir) {
        BlockPos clickedPos; byte face;
        if (blockHit != null) { clickedPos = blockHit.getBlockPos(); face = (byte) blockHit.getDirection().get3DDataValue(); }
        else { clickedPos = BlockPos.containing(hitLocation); face = 1; }
        var t = tag(); t.putInt("entityId", entityId); t.putLong("clickedPos", clickedPos.asLong());
        t.putByte("face", face); t.putDouble("hitX", hitLocation.x); t.putDouble("hitY", hitLocation.y); t.putDouble("hitZ", hitLocation.z);
        t.putByte("sourceType", (byte) NetworkConstants.INTERACT_EMPTY_HAND); t.putByte("toolSlot", (byte) 0);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.INTERACT_BLOCK, t));
    }

    public static void sendPauseWorkflow(int entryId) {
        var t = tag(); t.putInt("entryId", entryId);
        PacketDistributor.sendToServer(act(ActionType.PAUSE_WORKFLOW, t));
    }

    public static void sendDeleteWorkflow(int entryId) {
        var t = tag(); t.putInt("entryId", entryId);
        PacketDistributor.sendToServer(act(ActionType.DELETE_WORKFLOW, t));
    }

    // ── Linked-storage ↔ container menu transfers ──

    /**
     * Pick {@code amount} of the linked-storage item {@code prototype} into the
     * open container menu carried slot (server-authoritative).
     */
    public static void sendLinkedPickup(ItemStack prototype, int amount) {
        sendLinkedPickup(prototype, amount, false);
    }

    /**
     * Pick up {@code amount} of {@code prototype} into the carried slot.
     *
     * @param fromInventory {@code true} when the clicked grid entry sources from the player inventory
     *                      (extract only from player inventory instead of linked storage)
     */
    public static void sendLinkedPickup(ItemStack prototype, int amount, boolean fromInventory) {
        if (prototype == null || prototype.isEmpty() || amount <= 0) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var t = tag();
        t.put("prototype", prototype.saveOptional(level.registryAccess()));
        t.putInt("amount", amount);
        t.putBoolean("fromInventory", fromInventory);
        PacketDistributor.sendToServer(act(ActionType.LINKED_PICKUP, t));
    }

    /**
     * Return {@code amount} of the carried item matching {@code itemId}
     * (registry item id, e.g. {@code minecraft:dirt}) back to the linked storage.
     */
    public static void sendReturnCarried(String itemId, int amount) {
        if (itemId == null || itemId.isBlank() || amount <= 0) return;
        var t = tag(); t.putString("itemId", itemId); t.putInt("amount", amount);
        PacketDistributor.sendToServer(act(ActionType.RETURN_CARRIED, t));
    }

    /**
     * Shift-style quick move: push the linked-storage item {@code prototype}
     * straight into the open menu (single-item merge pass).
     */
    public static void sendLinkedQuickMove(ItemStack prototype) {
        sendLinkedQuickMove(prototype, false);
    }

    /**
     * Shift-style quick move: push the item straight into the open menu (single-item merge pass).
     *
     * @param fromInventory {@code true} when the clicked grid entry sources from the player inventory
     *                      (extract from player inventory and store into linked storage instead)
     */
    public static void sendLinkedQuickMove(ItemStack prototype, boolean fromInventory) {
        if (prototype == null || prototype.isEmpty()) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var t = tag();
        t.put("prototype", prototype.saveOptional(level.registryAccess()));
        t.putBoolean("fromInventory", fromInventory);
        PacketDistributor.sendToServer(act(ActionType.LINKED_QUICK_MOVE, t));
    }

    /**
     * Shift-click a slot of the open container menu: import the whole slot
     * (or one craft cycle for crafting tables) into linked storage.
     */
    public static void sendImportMenuSlot(int menuSlot) {
        if (menuSlot < 0) return;
        var t = tag(); t.putInt("slot", menuSlot);
        PacketDistributor.sendToServer(act(ActionType.IMPORT_MENU_SLOT, t));
    }

    // ── Funnel (item pickup) ──

    /**
     * 同步物品拾取（漏斗）开关状态到服务端。
     * 客户端点击左栏“物品拾取”按钮或按快捷键时发送，服务端据此开启/关闭漏斗能力。
     */
    public static void sendSetFunnelEnabled(boolean enabled) {
        var t = tag(); t.putBoolean("enabled", enabled);
        PacketDistributor.sendToServer(act(ActionType.SET_FUNNEL, t));
    }

    /**
     * 点击模式漏斗：以目标方块位置为球心、半径 2 格持续吸取掉落物到储存空间。
     * 服务端每 tick 最多提取 64 个物品，直到区域清空。
     */
    public static void sendFunnelPickup(BlockPos pos) {
        if (pos == null) return;
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.FUNNEL_PICKUP, t));
    }

    /**
     * 框选模式漏斗：客户端同步框选区域内的掉落物实体 ID 列表给服务端，
     * 服务端将这些实体一次性吸取到储存空间。
     */
    public static void sendFunnelBoxPickup(List<Integer> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return;
        var t = tag();
        var list = new ListTag();
        int limit = Math.min(entityIds.size(), 256);
        for (int i = 0; i < limit; i++) list.add(net.minecraft.nbt.IntTag.valueOf(entityIds.get(i)));
        t.put("entities", list);
        PacketDistributor.sendToServer(act(ActionType.FUNNEL_BOX_PICKUP, t));
    }
}
