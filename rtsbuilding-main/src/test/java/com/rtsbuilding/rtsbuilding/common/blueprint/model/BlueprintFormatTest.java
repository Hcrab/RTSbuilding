package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintFormatTest {

    @Test
    void extension() {
        assertEquals("nbt", BlueprintFormat.VANILLA_NBT.extension());
        assertEquals("schem", BlueprintFormat.SPONGE_SCHEM.extension());
        assertEquals("litematic", BlueprintFormat.LITEMATIC.extension());
        assertEquals("json", BlueprintFormat.BUILDING_GADGETS_JSON.extension());
    }

    @ParameterizedTest
    @CsvSource({
        "build.nbt, VANILLA_NBT",
        "house.nbt, VANILLA_NBT",
        "test.schem, SPONGE_SCHEM",
        "test.schematic, SPONGE_SCHEM",
        "build.litematic, LITEMATIC",
        "template.json, BUILDING_GADGETS_JSON",
        "noext, VANILLA_NBT",
        "unknown.xyz, VANILLA_NBT",
    })
    void fromFileName(String fileName, String expected) {
        assertEquals(BlueprintFormat.valueOf(expected), BlueprintFormat.fromFileName(fileName));
    }

    @Test
    void fromFileNameCaseInsensitive() {
        assertEquals(BlueprintFormat.SPONGE_SCHEM, BlueprintFormat.fromFileName("TEST.SCHEM"));
        assertEquals(BlueprintFormat.LITEMATIC, BlueprintFormat.fromFileName("HOUSE.LITEMATIC"));
        assertEquals(BlueprintFormat.BUILDING_GADGETS_JSON, BlueprintFormat.fromFileName("TEMPLATE.JSON"));
    }

    @Test
    void fromFileNameNullReturnsVanilla() {
        assertEquals(BlueprintFormat.VANILLA_NBT, BlueprintFormat.fromFileName(null));
    }

    @Test
    void fromFileNameEmptyReturnsVanilla() {
        assertEquals(BlueprintFormat.VANILLA_NBT, BlueprintFormat.fromFileName(""));
    }

    @Test
    void fromFileNameWithPath() {
        assertEquals(BlueprintFormat.LITEMATIC, BlueprintFormat.fromFileName("/path/to/build.litematic"));
    }

    @Test
    void ordinalStability() {
        assertEquals(0, BlueprintFormat.VANILLA_NBT.ordinal());
        assertEquals(1, BlueprintFormat.SPONGE_SCHEM.ordinal());
        assertEquals(2, BlueprintFormat.LITEMATIC.ordinal());
        assertEquals(3, BlueprintFormat.BUILDING_GADGETS_JSON.ordinal());
    }
}
