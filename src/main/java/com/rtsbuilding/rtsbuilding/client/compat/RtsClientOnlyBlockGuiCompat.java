package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 为明确审计过的“客户端自行开 GUI”方块补齐远程右键的客户端半边。
 *
 * <p>本类不替代服务端交互，也不泛化调用任意第三方方块。RTS 仍先发送权威 C2S，
 * 这里只有白名单方块会额外执行客户端 {@code onBlockActivated}。例如 Waystones 1.12
 * 的服务端分支只激活传送石，传送列表完全由客户端分支打开；缺少这一半时不会创建
 * Container，日志只能得到 {@code NO_MENU}。任何兼容异常都退回既有服务端路径。</p>
 */
@SideOnly(Side.CLIENT)
public final class RtsClientOnlyBlockGuiCompat {
    private static final ResourceLocation WAYSTONE = new ResourceLocation("waystones", "waystone");

    private RtsClientOnlyBlockGuiCompat() {
    }

    public static void tryOpenAfterAuthoritativeSend(RayTraceResult hit) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null
                || hit == null || hit.getBlockPos() == null) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        IBlockState state = minecraft.world.getBlockState(pos);
        ResourceLocation blockId = state.getBlock().getRegistryName();
        if (!WAYSTONE.equals(blockId)) {
            return;
        }

        EnumFacing face = hit.sideHit == null ? EnumFacing.UP : hit.sideHit;
        Vec3d hitVec = hit.hitVec == null
                ? new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                : hit.hitVec;
        float localX = clampUnit(hitVec.x - pos.getX());
        float localY = clampUnit(hitVec.y - pos.getY());
        float localZ = clampUnit(hitVec.z - pos.getZ());
        GuiScreen before = minecraft.currentScreen;
        try {
            state.getBlock().onBlockActivated(
                    minecraft.world,
                    pos,
                    state,
                    minecraft.player,
                    EnumHand.MAIN_HAND,
                    face,
                    localX,
                    localY,
                    localZ);
            if (minecraft.currentScreen != before) {
                RtsbuildingMod.LOGGER.info(
                        "[RTS-CLIENT] event=CLIENT_ONLY_GUI_OPEN block={} screen={}",
                        blockId,
                        minecraft.currentScreen == null
                                ? "null" : minecraft.currentScreen.getClass().getName());
            }
        } catch (RuntimeException | LinkageError failure) {
            RtsbuildingMod.LOGGER.warn(
                    "[RTS-CLIENT] event=CLIENT_ONLY_GUI_FALLBACK block={} reason={}",
                    blockId, failure.toString());
        }
    }

    private static float clampUnit(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }
}
