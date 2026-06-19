package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import net.minecraft.core.RegistryAccess;

/**
 * 蓝图读取器门面 —— 根据文件扩展名自动路由到对应的格式解析器。
 * <p>
 * 统一入口，调用方只需传入原始字节数据和文件名，
 * 无需关心底层使用了哪种格式解析引擎。
 */
public final class BlueprintReaders {

    private BlueprintReaders() {
    }

    /**
     * 解析蓝图文件数据并返回统一的 {@link RtsBlueprint} 对象。
     * <p>
     * 根据文件名自动检测格式并委派给对应的解析器。
     *
     * @param data           蓝图文件的原始字节数据
     * @param fileName       文件名（用于格式检测和错误报告）
     * @param registryAccess 注册表访问（用于解析方块状态）
     * @return 解析后的蓝图对象
     * @throws BlueprintParseException 如果解析失败
     */
    public static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess)
            throws BlueprintParseException {
        if (data == null || data.length == 0) {
            throw new BlueprintParseException("空的蓝图文件");
        }
        BlueprintFormat format = BlueprintFormat.fromFileName(fileName);
        return switch (format) {
            case VANILLA_NBT -> VanillaStructureNbtReader.parse(data, fileName, registryAccess);
            case SPONGE_SCHEM -> SpongeSchemReader.parse(data, fileName, registryAccess);
            case LITEMATIC -> LitematicReader.parse(data, fileName, registryAccess);
            case BUILDING_GADGETS_JSON -> BuildingGadgetsTemplateReader.parse(data, fileName, registryAccess);
        };
    }
}
