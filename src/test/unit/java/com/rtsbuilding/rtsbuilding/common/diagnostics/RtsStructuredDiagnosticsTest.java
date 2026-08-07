package com.rtsbuilding.rtsbuilding.common.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsStructuredDiagnosticsTest {
    @Test
    void escapesJsonlControlCharactersWithoutAddingPhysicalLines() {
        assertEquals("a\\n\\\"b\\\\c\\t", RtsStructuredDiagnostics.escape("a\n\"b\\c\t"));
    }

    @Test
    void exposesAllConfiguredDiagnosticLevels() {
        assertEquals(3, RtsDiagnosticLevel.values().length);
        assertEquals(RtsDiagnosticLevel.OFF, RtsDiagnosticLevel.valueOf("OFF"));
        assertEquals(RtsDiagnosticLevel.BASIC, RtsDiagnosticLevel.valueOf("BASIC"));
        assertEquals(RtsDiagnosticLevel.VERBOSE, RtsDiagnosticLevel.valueOf("VERBOSE"));
    }
}
