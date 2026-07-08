package com.rtsbuilding.rtsbuilding.blueprint.format;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintParseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintFormatRoutingTest {
    @Test
    void jsonFilesRouteToBuildingGadgetsFormat() {
        assertEquals(BlueprintFormat.BUILDING_GADGETS_JSON, BlueprintFormat.fromFileName("template.json"));
        assertEquals(BlueprintFormat.SPONGE_SCHEM, BlueprintFormat.fromFileName("template.schematic"));
        assertEquals(BlueprintFormat.LITEMATIC, BlueprintFormat.fromFileName("template.litematic"));
        assertEquals(BlueprintFormat.VANILLA_NBT, BlueprintFormat.fromFileName("template.nbt"));
    }

    @Test
    void jsonFilesAreParsedByBuildingGadgetsReaderInsteadOfVanillaNbt() {
        BlueprintParseException error = assertThrows(BlueprintParseException.class,
                () -> BlueprintReaders.parse("{}".getBytes(StandardCharsets.UTF_8), "template.json", null));

        assertTrue(error.getMessage().contains("Building Gadgets JSON is missing template data"),
                "JSON 蓝图必须进入 Building Gadgets reader，而不是回落到 vanilla NBT reader。");
    }
}
