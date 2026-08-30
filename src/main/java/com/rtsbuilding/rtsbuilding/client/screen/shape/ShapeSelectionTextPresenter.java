package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Supplier;

/**
 * 形状选择界面的纯文案 presenter。
 * <p>
 * 本类只把已经确定的形状、会话阶段、方块列表和确认键标签转换成玩家可见文本；
 * 它不读取屏幕、按键映射、配置、世界、预览缓存或网络状态。调用方仍负责生成真实
 * 形状位置、判断可破坏/可放置目标，并把当前语言的翻译函数传入。
 * <p>
 * 把这些规则集中在这里，可以让 Quick Build 正式面板、未来其他入口和离屏测试共享
 * 同一套状态文案，而不再要求 {@code ScreenShapeController} 同时维护世界算法与 UI 文案。
 */
public final class ShapeSelectionTextPresenter {
    private static final String ZERO_SIZE = "0*0*0";
    private static final String BLOCK_SIZE = "1*1*1";

    private ShapeSelectionTextPresenter() {
    }

    @FunctionalInterface
    public interface Translator {
        String text(String key, Object... args);
    }

    /**
     * 待确认状态所需的最小快照。会话可以为空，表示玩家尚未确定第一个点。
     */
    public record Status(
            boolean quickBuildOpen,
            BuildShape shape,
            boolean destroyMode,
            boolean chainDestroyMode,
            ShapeBuildTypes.Session session) {
    }

    public static String fillModeLabel(ShapeFillMode mode, Translator translator) {
        ShapeFillMode safeMode = mode == null ? ShapeFillMode.FILL : mode;
        return translator.text(switch (safeMode) {
            case FILL -> "screen.rtsbuilding.fill.fill";
            case HOLLOW -> "screen.rtsbuilding.fill.hollow";
            case SKELETON -> "screen.rtsbuilding.fill.skeleton";
        });
    }

    public static String dimensionLabel(BuildShape shape) {
        if (shape == null) {
            return "2D";
        }
        return switch (shape) {
            case LINE -> "1D";
            case CYLINDER, BALL, BOX -> "3D";
            default -> "2D";
        };
    }

    /**
     * 按真实方块包围盒生成 X*Y*Z 尺寸；重复位置不会改变尺寸。
     */
    public static String sizeText(BuildShape shape, List<BlockPos> blocks) {
        if (shape == BuildShape.BLOCK) {
            return BLOCK_SIZE;
        }
        if (blocks == null || blocks.isEmpty()) {
            return ZERO_SIZE;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;
        for (BlockPos pos : blocks) {
            if (pos == null) {
                continue;
            }
            found = true;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (!found) {
            return ZERO_SIZE;
        }

        long sizeX = (long) maxX - minX + 1L;
        long sizeY = (long) maxY - minY + 1L;
        long sizeZ = (long) maxZ - minZ + 1L;
        return sizeX + "*" + sizeY + "*" + sizeZ;
    }

    public static String countText(int count) {
        return Integer.toString(Math.max(0, count));
    }

    public static String pendingStatusText(
            Status status,
            Supplier<String> confirmKeyLabel,
            Translator translator) {
        if (status == null || !status.quickBuildOpen()) {
            return "";
        }

        BuildShape shape = status.shape() == null ? BuildShape.BLOCK : status.shape();
        if (status.chainDestroyMode()) {
            return translator.text("screen.rtsbuilding.shape_status.destroy_chain");
        }
        if (shape == BuildShape.BLOCK) {
            return translator.text(status.destroyMode()
                    ? "screen.rtsbuilding.shape_status.destroy"
                    : "screen.rtsbuilding.shape_status.place");
        }

        ShapeBuildTypes.Session session = status.session();
        if (session == null || session.shape() != shape || session.phase() == null) {
            return firstPointText(status.destroyMode(), translator);
        }
        return switch (session.phase()) {
            case NEED_SECOND_POINT -> {
                BlockPos pointA = session.pointA();
                if (pointA == null) {
                    yield firstPointText(status.destroyMode(), translator);
                }
                yield translator.text(status.destroyMode()
                                ? "screen.rtsbuilding.shape_status.destroy_step_b"
                                : "screen.rtsbuilding.shape_status.step_b",
                        pointA.getX(),
                        pointA.getY(),
                        pointA.getZ());
            }
            case NEED_THIRD_POINT -> translator.text(status.destroyMode()
                    ? "screen.rtsbuilding.shape_status.destroy_step_height"
                    : "screen.rtsbuilding.shape_status.step_height");
            case READY_CONFIRM -> translator.text(
                    confirmStatusKey(shape, status.destroyMode()),
                    confirmKeyLabel == null ? "" : confirmKeyLabel.get());
        };
    }

    public static String shapeLabel(BuildShape shape, Translator translator) {
        BuildShape safeShape = shape == null ? BuildShape.BLOCK : shape;
        return translator.text(switch (safeShape) {
            case BLOCK -> "screen.rtsbuilding.shape.block";
            case LINE -> "screen.rtsbuilding.shape.line";
            case SQUARE -> "screen.rtsbuilding.shape.square";
            case WALL -> "screen.rtsbuilding.shape.wall";
            case CIRCLE -> "screen.rtsbuilding.shape.circle";
            case CYLINDER -> "screen.rtsbuilding.shape.cylinder";
            case BALL -> "screen.rtsbuilding.shape.ball";
            case BOX -> "screen.rtsbuilding.shape.box";
        });
    }

    private static String firstPointText(boolean destroyMode, Translator translator) {
        return translator.text(destroyMode
                ? "screen.rtsbuilding.shape_status.destroy_step_a"
                : "screen.rtsbuilding.shape_status.step_a");
    }

    private static String confirmStatusKey(BuildShape shape, boolean destroyMode) {
        if (shape == BuildShape.WALL) {
            return destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy_confirm_wall"
                    : "screen.rtsbuilding.shape_status.confirm_wall";
        }
        if (shape == BuildShape.CYLINDER) {
            return destroyMode
                    ? "screen.rtsbuilding.shape_status.destroy_confirm_cylinder"
                    : "screen.rtsbuilding.shape_status.confirm_cylinder";
        }
        return destroyMode
                ? "screen.rtsbuilding.shape_status.destroy_confirm"
                : "screen.rtsbuilding.shape_status.confirm";
    }
}
