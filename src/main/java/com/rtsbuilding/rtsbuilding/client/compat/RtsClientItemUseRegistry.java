package com.rtsbuilding.rtsbuilding.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTS 模式下允许安全执行客户端物品 GUI 入口的注册表。
 *
 * <p>本类只负责客户端预测和打开普通 {@link Screen}，不发送原版交互包，也不执行
 * 服务端世界修改。未知物品默认不执行客户端代码，附属可以按物品 ID 注册明确的
 * 激活策略。</p>
 */
public final class RtsClientItemUseRegistry {
    public enum Activation {
        /** 普通右键和 Shift+右键均允许执行客户端 use/useOn。 */
        ALWAYS,
        /** 仅 Shift+右键允许执行，适合配置界面与普通发射共用右键的工具。 */
        SHIFT_ONLY
    }

    private static final Map<ResourceLocation, Activation> POLICIES = new ConcurrentHashMap<>();

    static {
        register(new ResourceLocation("minecraft", "written_book"), Activation.ALWAYS);
        register(new ResourceLocation("minecraft", "writable_book"), Activation.ALWAYS);
        register(new ResourceLocation("create", "handheld_worldshaper"), Activation.SHIFT_ONLY);
    }

    private RtsClientItemUseRegistry() {
    }

    /** 注册一个确认过客户端行为安全的物品；重复注册以最后一次为准。 */
    public static void register(ResourceLocation itemId, Activation activation) {
        if (itemId != null && activation != null) {
            POLICIES.put(itemId, activation);
        }
    }

    /**
     * 按原版“对块 useOn，PASS 后 use”的顺序执行一次客户端 GUI 预测。
     *
     * @return 本次调用是否打开了一个不同于当前 BuilderScreen 的客户端屏幕
     */
    public static boolean tryOpenRegisteredScreen(BlockHitResult blockHit, boolean shiftDown) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Activation activation = POLICIES.get(itemId);
        if (activation == null || activation == Activation.SHIFT_ONLY && !shiftDown) {
            return false;
        }

        Screen previousScreen = minecraft.screen;
        withTemporaryShift(player, shiftDown, () -> {
            InteractionResult useOnResult = InteractionResult.PASS;
            if (blockHit != null) {
                useOnResult = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, blockHit));
            }
            if (!useOnResult.consumesAction()) {
                // 只保留调用本身的客户端副作用；真实物品状态仍由后续服务端 RTS 动作同步。
                stack.use(minecraft.level, player, InteractionHand.MAIN_HAND);
            }
        });
        return minecraft.screen != null && minecraft.screen != previousScreen;
    }

    private static void withTemporaryShift(LocalPlayer player, boolean shiftDown, Runnable action) {
        boolean previousInput = player.input.shiftKeyDown;
        boolean previousEntity = player.isShiftKeyDown();
        player.input.shiftKeyDown = shiftDown;
        player.setShiftKeyDown(shiftDown);
        try {
            action.run();
        } finally {
            player.input.shiftKeyDown = previousInput;
            player.setShiftKeyDown(previousEntity);
        }
    }
}
