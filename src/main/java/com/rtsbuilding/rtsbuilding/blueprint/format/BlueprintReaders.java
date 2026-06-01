package com.rtsbuilding.rtsbuilding.blueprint.format;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import net.minecraft.core.RegistryAccess;

public final class BlueprintReaders {
    private BlueprintReaders() {
    }

    public static RtsBlueprint parse(byte[] data, String fileName, RegistryAccess registryAccess)
            throws BlueprintParseException {
        if (data == null || data.length == 0) {
            throw new BlueprintParseException("Empty blueprint file");
        }
        BlueprintFormat format = BlueprintFormat.fromFileName(fileName);
        return switch (format) {
            case VANILLA_NBT -> VanillaStructureNbtReader.parse(data, fileName, registryAccess);
            case SPONGE_SCHEM -> SpongeSchemReader.parse(data, fileName, registryAccess);
        };
    }
}
