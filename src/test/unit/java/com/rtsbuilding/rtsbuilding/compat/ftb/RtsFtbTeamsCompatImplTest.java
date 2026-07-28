package com.rtsbuilding.rtsbuilding.compat.ftb;

import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RtsFtbTeamsCompatImplTest {

    @Test
    void componentTeamNameUsesVisibleTextInsteadOfDebugStructure() throws ReflectiveOperationException {
        TextComponentString name = new TextComponentString("Short Team");
        name.setStyle(new Style().setBold(true));

        String result = FtbTeamReflection.plainTeamLabel(name);

        assertEquals("Short Team", result);
        assertFalse(result.contains("style="));
    }
}
