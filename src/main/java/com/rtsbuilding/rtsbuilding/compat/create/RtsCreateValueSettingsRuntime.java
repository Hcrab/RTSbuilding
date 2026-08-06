package com.rtsbuilding.rtsbuilding.compat.create;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Create 0.5.x Value Settings 的无硬依赖运行时门面。
 *
 * <p>本类只通过 Create 0.5.x 的公开行为接口寻找实际 ValueSettingsBehaviour、创建
 * 原生 board 并提交设置；它不识别方块 ID、不拥有 RTS 输入/网络/范围规则，也不会
 * 主动加载区块。所有 Create 类型都只保存在反射对象中，未安装 Create 时不会形成
 * 类加载依赖。</p>
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
    private static final String VALUE_SETTINGS_INPUT_HANDLER_CLASS =
            "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsInputHandler";
    private static final String ALL_BLOCKS_CLASS = "com.simibubi.create.AllBlocks";
    private static final String ALL_ITEM_TAGS_CLASS = "com.simibubi.create.AllTags$AllItemTags";

    private static boolean lookupAttempted;
    private static boolean available;
    private static Class<?> smartBlockEntityClass;
    private static Class<?> valueSettingsBehaviourClass;
    private static Class<?> valueSettingsBoardClass;
    private static Class<?> sidedValueBoxTransformClass;
    private static Method getAllBehaviours;
    private static Method isActive;
    private static Method acceptsValueSettings;
    private static Method onlyVisibleWithWrench;
    private static Method getSlotPositioning;
    private static Method testHit;
    private static Method bypassesInput;
    private static Method mayInteract;
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
    private static Object wrenchTag;
    private static Method wrenchMatches;
    private static Method canInteract;
    private static Field clipboard;
    private static Method clipboardAsItem;

    private RtsCreateValueSettingsRuntime() {
    }

    /**
     * 按 Create 0.5.x 原生输入门槛寻找当前命中位置可调节的行为。
     *
     * <p>复用 SmartBlockEntity 行为集合和实际命中盒，不识别具体机械。这里不做任何
     * 玩家实体近距判断；服务端调用方会使用 RTS 会话自己的产品范围。</p>
     */
    public static Candidate findEligible(Level level, BlockHitResult hit, Player player) {
        if (!ensureAvailable() || level == null || hit == null || player == null) {
            return null;
        }
        try {
            if (!passesCreateGlobalInputGate(player)) {
                return null;
            }
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
                        && !Boolean.TRUE.equals(wrenchMatches.invoke(wrenchTag, player.getMainHandItem()))) {
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
            // Create 缺失或小版本签名不符时必须安静回落到既有 RTS 输入。
        }
        return null;
    }

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

    /** 原生屏幕悬停时把预览值交还给实际行为。 */
    public static void notifyHovered(Candidate candidate, Object settings) {
        if (candidate == null || settings == null || !ensureAvailable()) {
            return;
        }
        try {
            newSettingHovered.invoke(candidate.behaviour(), settings);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 悬停预览不是保存前提，行为失效时让原生屏幕继续工作。
        }
    }

    /** 使用行为自己生成的 board 校验 row/value，不复制任何机械专属范围。 */
    public static boolean isValueAllowed(Object board, int row, int value) {
        if (board == null || !ensureAvailable() || !valueSettingsBoardClass.isInstance(board)) {
            return false;
        }
        try {
            Object rows = boardRows.invoke(board);
            Object maximum = boardMaxValue.invoke(board);
            return rows instanceof List<?> list
                    && maximum instanceof Number max
                    && row >= 0
                    && row < list.size()
                    && value >= 0
                    && value <= max.intValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static void applyValue(
            Candidate candidate, Player player, int row, int value, boolean ctrlDown) {
        if (candidate == null || player == null || !ensureAvailable()) {
            return;
        }
        try {
            Object settings = valueSettingsConstructor.newInstance(row, value);
            setValueSettings.invoke(candidate.behaviour(), player, settings, ctrlDown);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 点击后目标被拆除或行为失效时，保持与原生包一致的安全失败。
        }
    }

    /** 执行 Create 1.20.1 的原生短按语义，不把短按伪装成数值设置。 */
    public static void applyShortInteraction(
            Candidate candidate, Player player, Direction face, BlockHitResult hit) {
        if (candidate == null || player == null || face == null || hit == null || !ensureAvailable()) {
            return;
        }
        try {
            onShortInteract.invoke(
                    candidate.behaviour(), player, InteractionHand.MAIN_HAND, face, hit);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 目标在释放前失效时安静忽略。
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

    /**
     * 复用 Create 在扫描数值行为前使用的全局输入许可。
     *
     * <p>不要在 RTS 内复制潜行、旁观或冒险模式的判断；Create 的 {@code canInteract} 是
     * 唯一事实来源。任何反射失败均让出输入，防止错误截获 Create 原生右键。</p>
     */
    private static boolean passesCreateGlobalInputGate(Player player)
            throws ReflectiveOperationException {
        boolean createCanInteract = isTrue(canInteract, null, player);
        Object clipboardItem = clipboardAsItem.invoke(clipboard.get(null));
        return clipboardItem instanceof Item item
                && RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(
                        createCanInteract, player.getMainHandItem().is(item));
    }

    private static boolean ensureAvailable() {
        if (lookupAttempted) {
            return available;
        }
        lookupAttempted = true;
        try {
            smartBlockEntityClass = Class.forName(SMART_BLOCK_ENTITY_CLASS);
            valueSettingsBehaviourClass = Class.forName(VALUE_SETTINGS_BEHAVIOUR_CLASS);
            Class<?> valueSettingsClass = Class.forName(VALUE_SETTINGS_CLASS);
            valueSettingsBoardClass = Class.forName(VALUE_SETTINGS_BOARD_CLASS);
            sidedValueBoxTransformClass = Class.forName(SIDED_VALUE_BOX_TRANSFORM_CLASS);
            Class<?> valueSettingsInputHandlerClass = Class.forName(VALUE_SETTINGS_INPUT_HANDLER_CLASS);
            Class<?> allBlocksClass = Class.forName(ALL_BLOCKS_CLASS);
            Class<?> allItemTagsClass = Class.forName(ALL_ITEM_TAGS_CLASS);

            getAllBehaviours = smartBlockEntityClass.getMethod("getAllBehaviours");
            isActive = valueSettingsBehaviourClass.getMethod("isActive");
            acceptsValueSettings = valueSettingsBehaviourClass.getMethod("acceptsValueSettings");
            onlyVisibleWithWrench = valueSettingsBehaviourClass.getMethod("onlyVisibleWithWrench");
            getSlotPositioning = valueSettingsBehaviourClass.getMethod("getSlotPositioning");
            testHit = valueSettingsBehaviourClass.getMethod("testHit", Vec3.class);
            bypassesInput = valueSettingsBehaviourClass.getMethod("bypassesInput", ItemStack.class);
            mayInteract = valueSettingsBehaviourClass.getMethod("mayInteract", Player.class);
            netId = valueSettingsBehaviourClass.getMethod("netId");
            createBoard = valueSettingsBehaviourClass.getMethod(
                    "createBoard", Player.class, BlockHitResult.class);
            getValueSettings = valueSettingsBehaviourClass.getMethod("getValueSettings");
            newSettingHovered = valueSettingsBehaviourClass.getMethod(
                    "newSettingHovered", valueSettingsClass);
            setValueSettings = valueSettingsBehaviourClass.getMethod(
                    "setValueSettings", Player.class, valueSettingsClass, boolean.class);
            onShortInteract = valueSettingsBehaviourClass.getMethod(
                    "onShortInteract", Player.class, InteractionHand.class,
                    Direction.class, BlockHitResult.class);
            sidedIsSideActive = sidedValueBoxTransformClass.getDeclaredMethod(
                    "isSideActive", BlockState.class, Direction.class);
            sidedIsSideActive.setAccessible(true);
            sidedFromSide = sidedValueBoxTransformClass.getMethod("fromSide", Direction.class);
            boardRows = valueSettingsBoardClass.getMethod("rows");
            boardMaxValue = valueSettingsBoardClass.getMethod("maxValue");
            valueSettingsConstructor = valueSettingsClass.getConstructor(int.class, int.class);

            Field wrenchField = allItemTagsClass.getField("WRENCH");
            wrenchTag = wrenchField.get(null);
            wrenchMatches = wrenchTag.getClass().getMethod("matches", ItemStack.class);
            canInteract = valueSettingsInputHandlerClass.getMethod("canInteract", Player.class);
            clipboard = allBlocksClass.getField("CLIPBOARD");
            clipboardAsItem = clipboard.getType().getMethod("asItem");
            available = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            available = false;
        }
        return available;
    }

    /** 一次点击/包处理期间解析出的实际 Create 行为，不跨 tick 或区块持久化。 */
    public record Candidate(BlockEntity blockEntity, Object behaviour, int netId) {
    }
}
