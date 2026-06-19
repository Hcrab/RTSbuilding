package com.rtsbuilding.rtsbuilding.common.blueprint.model;

/**
 * 蓝图格式枚举 —— 标识 RTSbuilding 支持的蓝图文件格式。
 * <p>
 * 每种格式对应一个文件扩展名，用于在导入时自动识别并路由到对应的解析器。
 * 注意：枚举序数（ordinal）在网络传输中被使用，因此必须保持稳定。
 */
public enum BlueprintFormat {
    /** 原版 Minecraft 结构 NBT 格式（.nbt） */
    VANILLA_NBT("nbt"),
    /** Sponge 模组生态的 Schematic 格式（.schem / .schematic） */
    SPONGE_SCHEM("schem"),
    /** Litematica 模组的 Litematic 格式（.litematic） */
    LITEMATIC("litematic"),
    /** Building Gadgets 模组的 JSON 模板格式（.json） */
    BUILDING_GADGETS_JSON("json");

    /** 该格式对应的文件扩展名（不含点号） */
    private final String extension;

    BlueprintFormat(String extension) {
        this.extension = extension;
    }

    /**
     * 获取该格式对应的文件扩展名。
     *
     * @return 扩展名字符串，如 "nbt"、"schem"
     */
    public String extension() {
        return this.extension;
    }

    /**
     * 根据文件名推断蓝图格式。
     * <p>
     * 通过文件扩展名匹配对应的格式，不匹配任何已知扩展名时默认为原版 NBT 格式。
     *
     * @param fileName 文件名（可含路径）
     * @return 匹配的蓝图格式枚举值
     */
    public static BlueprintFormat fromFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
            return SPONGE_SCHEM;
        }
        if (lower.endsWith(".litematic")) {
            return LITEMATIC;
        }
        if (lower.endsWith(".json")) {
            return BUILDING_GADGETS_JSON;
        }
        return VANILLA_NBT;
    }
}
