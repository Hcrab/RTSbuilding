package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 高级破坏参数存储 + 范围校验。
 */
public final class AdvancedDestroyOptions {

    private AdvancedDestroySubMode subMode = AdvancedDestroySubMode.RECTANGLE;

    // ===== 矩形 =====
    private int rectPlusX = ADV_DESTROY_RECT_DEFAULT;
    private int rectMinusX = ADV_DESTROY_RECT_DEFAULT;
    private int rectPlusY = ADV_DESTROY_RECT_DEFAULT;
    private int rectMinusY = ADV_DESTROY_RECT_DEFAULT;
    private int rectPlusZ = ADV_DESTROY_RECT_DEFAULT;
    private int rectMinusZ = ADV_DESTROY_RECT_DEFAULT;
    private ShapeFillMode rectFillMode = ShapeFillMode.FILL;

    // ===== 圆柱 =====
    private int cylinderRadius = ADV_DESTROY_CYLINDER_RADIUS_DEFAULT;
    private int cylinderPlusH = ADV_DESTROY_CYLINDER_HEIGHT_DEFAULT;
    private int cylinderMinusH = ADV_DESTROY_CYLINDER_HEIGHT_DEFAULT;
    private ShapeFillMode cylinderFillMode = ShapeFillMode.FILL;

    // ===== 楼梯 =====
    private int stairsCount = ADV_DESTROY_STAIRS_COUNT_DEFAULT;
    private int stairsRotation = 0; // 0 / 90 / 180 / 270
    private boolean stairsSymmetric = false;

    // ===== 伐木 =====
    private int lumberLimit = ADV_DESTROY_LUMBER_LIMIT_DEFAULT;
    private boolean lumberStrongMan = false; // 光头强附体：解锁至 32768
    private boolean lumberAllowPlayerBlocks = false; // 允许破坏玩家造物

    // ======================== getter / setter ========================

    public AdvancedDestroySubMode getSubMode() { return subMode; }
    public void setSubMode(AdvancedDestroySubMode mode) { this.subMode = mode; }

    public int getRectPlusX() { return rectPlusX; }
    public void setRectPlusX(int v) { this.rectPlusX = clampRect(v); }
    public int getRectMinusX() { return rectMinusX; }
    public void setRectMinusX(int v) { this.rectMinusX = clampRect(v); }
    public int getRectPlusY() { return rectPlusY; }
    public void setRectPlusY(int v) { this.rectPlusY = clampRect(v); }
    public int getRectMinusY() { return rectMinusY; }
    public void setRectMinusY(int v) { this.rectMinusY = clampRect(v); }
    public int getRectPlusZ() { return rectPlusZ; }
    public void setRectPlusZ(int v) { this.rectPlusZ = clampRect(v); }
    public int getRectMinusZ() { return rectMinusZ; }
    public void setRectMinusZ(int v) { this.rectMinusZ = clampRect(v); }
    public ShapeFillMode getRectFillMode() { return rectFillMode; }
    public void setRectFillMode(ShapeFillMode mode) {
        this.rectFillMode = validateRectFillMode(mode);
    }

    public int getCylinderRadius() { return cylinderRadius; }
    public void setCylinderRadius(int v) { this.cylinderRadius = clampCylinderRadius(v); }
    public int getCylinderPlusH() { return cylinderPlusH; }
    public void setCylinderPlusH(int v) { this.cylinderPlusH = clampCylinderHeight(v); }
    public int getCylinderMinusH() { return cylinderMinusH; }
    public void setCylinderMinusH(int v) { this.cylinderMinusH = clampCylinderHeight(v); }
    public ShapeFillMode getCylinderFillMode() { return cylinderFillMode; }
    public void setCylinderFillMode(ShapeFillMode mode) {
        this.cylinderFillMode = validateCylinderFillMode(mode);
    }

    public int getStairsCount() { return stairsCount; }
    public void setStairsCount(int v) { this.stairsCount = clampStairsCount(v); }
    public int getStairsRotation() { return stairsRotation; }
    public void setStairsRotation(int deg) {
        this.stairsRotation = Math.floorMod(deg, 360);
        // 量化为 0/90/180/270
        this.stairsRotation = Math.round(this.stairsRotation / 90.0f) * 90 % 360;
    }
    public boolean isStairsSymmetric() { return stairsSymmetric; }
    public void setStairsSymmetric(boolean v) { this.stairsSymmetric = v; }

    public int getLumberLimit() { return lumberLimit; }
    public void setLumberLimit(int v) { this.lumberLimit = clampLumberLimit(v); }
    public boolean isLumberStrongMan() { return lumberStrongMan; }
    public void setLumberStrongMan(boolean v) { this.lumberStrongMan = v; }
    public boolean isLumberAllowPlayerBlocks() { return lumberAllowPlayerBlocks; }
    public void setLumberAllowPlayerBlocks(boolean v) { this.lumberAllowPlayerBlocks = v; }
    /** 当前有效的伐木限制（光头强附体时使用硬上限） */
    public int effectiveLumberLimit() {
        return lumberStrongMan ? ADV_DESTROY_LUMBER_HARD_LIMIT : lumberLimit;
    }

    // ======================== 范围校验 ========================

    private static int clampRect(int v) {
        return Mth.clamp(v, ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX);
    }
    private static int clampCylinderRadius(int v) {
        return Mth.clamp(v, ADV_DESTROY_CYLINDER_RADIUS_MIN, ADV_DESTROY_CYLINDER_RADIUS_MAX);
    }
    private static int clampCylinderHeight(int v) {
        return Mth.clamp(v, ADV_DESTROY_CYLINDER_HEIGHT_MIN, ADV_DESTROY_CYLINDER_HEIGHT_MAX);
    }
    private static int clampStairsCount(int v) {
        return Mth.clamp(v, ADV_DESTROY_STAIRS_COUNT_MIN, ADV_DESTROY_STAIRS_COUNT_MAX);
    }
    private static int clampLumberLimit(int v) {
        return Mth.clamp(v, ADV_DESTROY_LUMBER_LIMIT_MIN, ADV_DESTROY_LUMBER_LIMIT_MAX);
    }
    private static ShapeFillMode validateRectFillMode(ShapeFillMode mode) {
        if (mode == ShapeFillMode.FILL || mode == ShapeFillMode.HOLLOW || mode == ShapeFillMode.SKELETON) return mode;
        return ShapeFillMode.FILL;
    }
    private static ShapeFillMode validateCylinderFillMode(ShapeFillMode mode) {
        if (mode == ShapeFillMode.FILL || mode == ShapeFillMode.HOLLOW) return mode;
        return ShapeFillMode.FILL;
    }
}
