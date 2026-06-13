package com.rtsbuilding.rtsbuilding.progression.node;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 科技树节??ID 常量集中定义??
 * 与树结构定义解耦，方便其他模块引用??
 */
public final class RtsProgressionNodeId {
    // ─── 相机与基础 ───
    public static final ResourceLocation CAMERA_CORE = id("camera_core");
    public static final ResourceLocation RADIUS_1 = id("radius_1");
    public static final ResourceLocation RADIUS_2 = id("radius_2");
    public static final ResourceLocation RADIUS_3 = id("radius_3");
    public static final ResourceLocation RADIUS_MAX = id("radius_max");
    public static final ResourceLocation FIELD_DEPLOYMENT = id("field_deployment");

    // ─── 存储与远程操??───
    public static final ResourceLocation STORAGE_LINK = id("storage_link");
    public static final ResourceLocation REMOTE_PLACE = id("remote_place");
    public static final ResourceLocation REMOTE_BREAK = id("remote_break");
    public static final ResourceLocation ROTATE_BLOCK = id("rotate_block");
    public static final ResourceLocation AUTO_STORE_MINED = id("auto_store_mined");
    public static final ResourceLocation FUNNEL = id("funnel");
    public static final ResourceLocation FLUID_BUFFER = id("fluid_buffer");

    // ─── 远程 GUI 与合??───
    public static final ResourceLocation REMOTE_GUI = id("remote_gui");
    public static final ResourceLocation CRAFT_TERMINAL = id("craft_terminal");
    public static final ResourceLocation JEI_TRANSFER = id("jei_transfer");

    // ─── 挖掘与蓝??───
    public static final ResourceLocation ULTIMINE = id("ultimine");
    public static final ResourceLocation AREA_DESTROY = id("area_destroy");
    public static final ResourceLocation BLUEPRINTS = id("blueprints");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(RtsbuildingMod.MODID, path);
    }

    private RtsProgressionNodeId() {
    }
}
