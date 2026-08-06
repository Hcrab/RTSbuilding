package com.rtsbuilding.rtsbuilding.compat.create;

import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Create Value Settings 的无硬依赖运行时门面。
 *
 * <p>本类只负责通过 Create 的公开运行时行为接口定位、复核和提交 ValueSettingsBehaviour；它不拥有
 * RTS 输入、屏幕生命周期、网络注册或任何距离规则。所有 Create 类型都仅在反射解析后保存在这里，
 * 因此未安装 Create、版本不兼容或反射失败时，调用方可安静地回落到自己的正常流程。</p>
 */
public final class RtsCreateValueSettingsRuntime {
    private static final String SMART_BLOCK_ENTITY_CLASS =
            "com.simibubi.create.foundation.blockEntity.SmartBlockEntity";
    private static final String VALUE_SETTINGS_BEHAVIOUR_CLASS =
            "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour";
    private static final String VALUE_SETTINGS_CLASS =
            "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour$ValueSettings";
    private static final String VALUE_SETTINGS_BOARD_CLASS =
            "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard";
    private static final String SIDED_VALUE_BOX_TRANSFORM_CLASS =
            "com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform$Sided";

    private static boolean lookupAttempted;
    private static boolean available;
    private static Class<?> smartBlockEntityClass;
    private static Class<?> valueSettingsBehaviourClass;
    private static Class<?> valueSettingsClass;
    private static Class<?> valueSettingsBoardClass;
    private static Class<?> sidedValueBoxTransformClass;
    private static Method getAllBehaviours;
    private static Method isActive;
    private static Method acceptsValueSettings;
    private static Method onlyVisibleWithWrench;
    private static Method bypassesInput;
    private static Method mayInteract;
    private static Method getSlotPositioning;
    private static Method testHit;
    private static Method netId;
    private static Method createBoard;
    private static Method getValueSettings;
    private static Method newSettingHovered;
    private static Method setValueSettings;
    private static Method onShortInteract;
    private static Method sidedIsSideActive;
    private static Method sidedFromSide;
    private static Method boardRows;
    private static Method boardMaxValue;
    private static Constructor<?> valueSettingsConstructor;

    private RtsCreateValueSettingsRuntime() {
    }

    /**
     * 按 Create 原生输入门槛寻找当前命中位置可操作的行为。
     *
     * <p>这里复用 SmartBlockEntity 的行为集合而不是识别方块 ID，并保留 Create 对潜行、旁观、扳手、
     * side transform、命中框、active、acceptsValueSettings 与 mayInteract 的客户端前置判断。
     * 它不判断玩家到目标的距离；RTS 的远程资格由调用方的服务端会话校验负责。</p>
     */
    public static Candidate findEligible(Level level, BlockHitResult hit, Player player) {
        if (!ensureAvailable() || level == null || hit == null || player == null
                || player.isSpectator() || player.isShiftKeyDown()) {
            return null;
        }
        try {
            BlockEntity blockEntity = level.getBlockEntity(hit.getBlockPos());
            if (!smartBlockEntityClass.isInstance(blockEntity)) {
                return null;
            }
            Object behaviours = getAllBehaviours.invoke(blockEntity);
            if (!(behaviours instanceof Collection<?> collection)) {
                return null;
            }
            for (Object behaviour : collection) {
                if (!valueSettingsBehaviourClass.isInstance(behaviour)
                        || !isTrue(isActive, behaviour)
                        || !isTrue(acceptsValueSettings, behaviour)
                        || !isTrue(mayInteract, behaviour, player)
                        || isTrue(bypassesInput, behaviour, player.getMainHandItem())) {
                    continue;
                }
                if (isTrue(onlyVisibleWithWrench, behaviour)
                        && !player.getMainHandItem().is(wrenchTag())) {
                    continue;
                }
                if (!prepareSidedTransform(behaviour, blockEntity.getBlockState(), hit.getDirection())
                        || !isTrue(testHit, behaviour, hit.getLocation())) {
                    continue;
                }
                Object id = netId.invoke(behaviour);
                if (id instanceof Number number) {
                    return new Candidate(blockEntity, behaviour, number.intValue());
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 可选兼容失败必须回落，不能让 Create 的小版本差异打断 RTS 输入。
        }
        return null;
    }

    /**
     * 创建 Create 原生屏幕和服务端复核共用的 board。
     *
     * <p>board 仍由实际行为创建，因此行数、最大值和格式语义始终以 Create/目标方块为准，
     * 本项目不复制或猜测各类机械的设置范围。</p>
     */
    public static Object createBoard(Candidate candidate, Player player, BlockHitResult hit) {
        if (candidate == null || player == null || hit == null || !ensureAvailable()) {
            return null;
        }
        try {
            return createBoard.invoke(candidate.behaviour(), player, hit);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    /** 返回行为当前的 Create ValueSettings 记录，供原生屏幕定位初始光标。 */
    public static Object currentSettings(Candidate candidate) {
        if (candidate == null || !ensureAvailable()) {
            return null;
        }
        try {
            return getValueSettings.invoke(candidate.behaviour());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    /** 保留 Create 原生 ValueSettingsScreen 的悬停预览回调。 */
    public static void notifyHovered(Candidate candidate, Object settings) {
        if (candidate == null || settings == null || !ensureAvailable()) {
            return;
        }
        try {
            newSettingHovered.invoke(candidate.behaviour(), settings);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 悬停预览不是保存前提，失败时让原生屏幕继续工作即可。
        }
    }

    /**
     * 以 Create 自己的 ValueSettingsBoard 语义核验客户端提交的 row/value。
     *
     * <p>该校验不引入任意自定义范围：行只能是 board 已声明的行，值只能落在该 board 的 maxValue 内。</p>
     */
    public static boolean isValueAllowed(Object board, int row, int value) {
        if (board == null || !ensureAvailable() || !valueSettingsBoardClass.isInstance(board)) {
            return false;
        }
        try {
            Object rows = boardRows.invoke(board);
            Object maxValue = boardMaxValue.invoke(board);
            return rows instanceof List<?> list
                    && maxValue instanceof Number maximum
                    && row >= 0
                    && row < list.size()
                    && value >= 0
                    && value <= maximum.intValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /** 将经过 board 核验的值交回 Create 行为，不负责距离或区块加载判断。 */
    public static void applyValue(Candidate candidate, Player player, int row, int value, boolean ctrlDown) {
        if (candidate == null || player == null || !ensureAvailable()) {
            return;
        }
        try {
            Object settings = valueSettingsConstructor.newInstance(row, value);
            setValueSettings.invoke(candidate.behaviour(), player, settings, ctrlDown);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 行为在提交期间失效时安全忽略，避免让服务器网络线程抛出 Create 反射异常。
        }
    }

    /** 执行 Create 原生短按语义，不把 short click 伪装成一项 value 设置。 */
    public static void applyShortInteraction(Candidate candidate, Player player, Direction face, BlockHitResult hit) {
        if (candidate == null || player == null || face == null || hit == null || !ensureAvailable()) {
            return;
        }
        try {
            onShortInteract.invoke(candidate.behaviour(), player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, face, hit);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 行为可能在点击和服务端处理之间被拆除；这与普通 Create 包的安全失败语义一致。
        }
    }

    private static boolean prepareSidedTransform(Object behaviour, BlockState state, Direction face)
            throws ReflectiveOperationException {
        Object positioning = getSlotPositioning.invoke(behaviour);
        if (positioning == null || !sidedValueBoxTransformClass.isInstance(positioning)) {
            return true;
        }
        if (!isTrue(sidedIsSideActive, positioning, state, face)) {
            return false;
        }
        sidedFromSide.invoke(positioning, face);
        return true;
    }

    private static boolean isTrue(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        return Boolean.TRUE.equals(method.invoke(target, arguments));
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Item> wrenchTag() {
        return Tags.Items.TOOLS_WRENCH;
    }

    private static boolean ensureAvailable() {
        if (lookupAttempted) {
            return available;
        }
        lookupAttempted = true;
        try {
            smartBlockEntityClass = Class.forName(SMART_BLOCK_ENTITY_CLASS);
            valueSettingsBehaviourClass = Class.forName(VALUE_SETTINGS_BEHAVIOUR_CLASS);
            valueSettingsClass = Class.forName(VALUE_SETTINGS_CLASS);
            valueSettingsBoardClass = Class.forName(VALUE_SETTINGS_BOARD_CLASS);
            sidedValueBoxTransformClass = Class.forName(SIDED_VALUE_BOX_TRANSFORM_CLASS);

            getAllBehaviours = smartBlockEntityClass.getMethod("getAllBehaviours");
            isActive = valueSettingsBehaviourClass.getMethod("isActive");
            acceptsValueSettings = valueSettingsBehaviourClass.getMethod("acceptsValueSettings");
            onlyVisibleWithWrench = valueSettingsBehaviourClass.getMethod("onlyVisibleWithWrench");
            bypassesInput = valueSettingsBehaviourClass.getMethod("bypassesInput", net.minecraft.world.item.ItemStack.class);
            mayInteract = valueSettingsBehaviourClass.getMethod("mayInteract", Player.class);
            getSlotPositioning = valueSettingsBehaviourClass.getMethod("getSlotPositioning");
            testHit = valueSettingsBehaviourClass.getMethod("testHit", net.minecraft.world.phys.Vec3.class);
            netId = valueSettingsBehaviourClass.getMethod("netId");
            createBoard = valueSettingsBehaviourClass.getMethod("createBoard", Player.class, BlockHitResult.class);
            getValueSettings = valueSettingsBehaviourClass.getMethod("getValueSettings");
            newSettingHovered = valueSettingsBehaviourClass.getMethod("newSettingHovered", valueSettingsClass);
            setValueSettings = valueSettingsBehaviourClass.getMethod(
                    "setValueSettings", Player.class, valueSettingsClass, boolean.class);
            onShortInteract = valueSettingsBehaviourClass.getMethod(
                    "onShortInteract", Player.class, net.minecraft.world.InteractionHand.class,
                    Direction.class, BlockHitResult.class);
            sidedIsSideActive = sidedValueBoxTransformClass.getDeclaredMethod(
                    "isSideActive", BlockState.class, Direction.class);
            sidedIsSideActive.setAccessible(true);
            sidedFromSide = sidedValueBoxTransformClass.getMethod("fromSide", Direction.class);
            boardRows = valueSettingsBoardClass.getMethod("rows");
            boardMaxValue = valueSettingsBoardClass.getMethod("maxValue");
            valueSettingsConstructor = valueSettingsClass.getConstructor(int.class, int.class);
            available = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            available = false;
        }
        return available;
    }

    /**
     * 一次解析出的实际 Create 行为。
     *
     * <p>候选只在同一客户端点击或同一服务端包处理期间使用；不跨区块保存，也不替代 Create 的行为状态。</p>
     */
    public record Candidate(BlockEntity blockEntity, Object behaviour, int netId) {
    }
}
