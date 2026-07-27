package com.rtsbuilding.rtsbuilding.common.blueprint.io;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;

/** 根据扩展名路由到各蓝图格式解析器；1.12 的静态注册表无需 RegistryAccess。 */
public final class BlueprintReaders {
    private BlueprintReaders() {}

    public static RtsBlueprint parse(byte[] data, String fileName) throws BlueprintParseException {
        if (data == null || data.length == 0) throw new BlueprintParseException("空的蓝图文件");
        BlueprintFormat format = BlueprintFormat.fromFileName(fileName);
        switch (format) {
            case VANILLA_NBT: return VanillaStructureNbtReader.parse(data, fileName);
            case SPONGE_SCHEM: return SpongeSchemReader.parse(data, fileName);
            case LITEMATIC: return LitematicReader.parse(data, fileName);
            case BUILDING_GADGETS_JSON: return BuildingGadgetsTemplateReader.parse(data, fileName);
            default: throw new BlueprintParseException("不支持的蓝图格式: " + format);
        }
    }

    /** 迁移期兼容入口；第三个参数在 1.12 中不参与解析。 */
    public static RtsBlueprint parse(byte[] data, String fileName, Object ignoredRegistryAccess)
            throws BlueprintParseException {
        return parse(data, fileName);
    }
}
