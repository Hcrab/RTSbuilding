package com.rtsbuilding.rtsbuilding.compat.ftb;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RtsFtbTeamsCompatImplTest {

    @Test
    void componentTeamNameUsesVisibleTextInsteadOfDebugStructure() {
        Component name = Component.literal("Short Team").withStyle(style -> style.withBold(true));

        String result = RtsFtbTeamsCompatImpl.plainTeamLabel(Optional.of(name));

        assertEquals("Short Team", result);
        assertFalse(result.contains("style="));
    }
}
