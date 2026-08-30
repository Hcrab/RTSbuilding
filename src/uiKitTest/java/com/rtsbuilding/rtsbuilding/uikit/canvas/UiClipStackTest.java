package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiClipStackTest {
    @Test
    void childClipCannotExpandBeyondParent() {
        UiClipStack clips = new UiClipStack();
        assertEquals(new UiRect(10, 20, 100, 80), clips.push(new UiRect(10, 20, 100, 80)));
        assertEquals(new UiRect(10, 20, 20, 10), clips.push(new UiRect(0, 0, 30, 30)));
        assertEquals(new UiRect(10, 20, 100, 80), clips.pop());
        assertNull(clips.pop());
    }

    @Test
    void disjointClipBecomesEmptyAndUnderflowFails() {
        UiClipStack clips = new UiClipStack();
        clips.push(new UiRect(0, 0, 10, 10));
        assertEquals(UiRect.EMPTY, clips.push(new UiRect(20, 20, 5, 5)));
        clips.pop();
        clips.pop();
        assertThrows(IllegalStateException.class, clips::pop);
    }
}
