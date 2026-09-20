package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 设置保存按钮的实际 screen-owned 资格判断。 */
final class RtsModConfigScreenLifecycleTest {
    @Test
    void saveRequiresWorldAuthorityAndAnUnsubmittedDraft() {
        assertTrue(RtsModConfigScreen.worldSaveEnabled(true, true, true, false, true));
        assertFalse(RtsModConfigScreen.worldSaveEnabled(false, true, true, false, true));
        assertFalse(RtsModConfigScreen.worldSaveEnabled(true, false, true, false, true));
        assertFalse(RtsModConfigScreen.worldSaveEnabled(true, true, false, false, true));
        assertFalse(RtsModConfigScreen.worldSaveEnabled(true, true, true, true, true));
        assertFalse(RtsModConfigScreen.worldSaveEnabled(true, true, true, false, false));
    }

    @Test
    void draftValueSurvivesTheSameSessionLayoutRebuild() {
        RtsServerConfigDraft draft = RtsServerConfigDraft.from(
                RtsServerConfigView.defaults().readOnly());
        draft.setText(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X, "80");
        // resize 分支只重建生产布局结果，不调用 draft.accept(view)。
        RtsModConfigLayout.world(100);
        RtsModConfigLayout.world(76);
        assertTrue(draft.isDirty());
        assertEquals("80", draft.text(RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X));
    }
}
