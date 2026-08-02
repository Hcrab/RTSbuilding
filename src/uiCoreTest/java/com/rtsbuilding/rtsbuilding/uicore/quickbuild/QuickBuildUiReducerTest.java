package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildUiReducerTest {
    @Test
    void lockedDestroyModeCannotBecomeActive() {
        QuickBuildUiState state = state(false, QuickBuildUiMode.BUILD, QuickBuildUiShape.BLOCK);
        QuickBuildUiTransition result = QuickBuildUiReducer.apply(state,
                QuickBuildUiAction.mode(QuickBuildUiMode.DESTROY));
        assertEquals(QuickBuildUiTransition.Command.NONE, result.command);
        assertEquals(QuickBuildUiMode.BUILD, result.state.mode);
    }

    @Test
    void shapeAndFillSelectionRemainSingleAndModeSpecific() {
        QuickBuildUiState state = state(true, QuickBuildUiMode.DESTROY, QuickBuildUiShape.CHAIN);
        QuickBuildUiTransition shape = QuickBuildUiReducer.apply(state,
                QuickBuildUiAction.shape(QuickBuildUiShape.BOX));
        assertEquals(QuickBuildUiShape.BOX, shape.state.destroyShape);
        assertEquals(QuickBuildUiShape.BLOCK, shape.state.buildShape);

        QuickBuildUiTransition hollow = QuickBuildUiReducer.apply(shape.state,
                QuickBuildUiAction.control(QuickBuildUiControl.Id.HOLLOW));
        assertTrue(hollow.state.control(QuickBuildUiControl.Id.HOLLOW).selected);
        assertFalse(hollow.state.control(QuickBuildUiControl.Id.FILL).selected);
    }

    @Test
    void chainLimitIsClampedAndOnlyChangesInChainMode() {
        QuickBuildUiState chain = state(true, QuickBuildUiMode.DESTROY, QuickBuildUiShape.CHAIN);
        assertEquals(512, QuickBuildUiReducer.apply(chain,
                QuickBuildUiAction.limit(9999)).state.chainLimit);
        QuickBuildUiState box = state(true, QuickBuildUiMode.DESTROY, QuickBuildUiShape.BOX);
        assertEquals(QuickBuildUiTransition.Command.NONE,
                QuickBuildUiReducer.apply(box, QuickBuildUiAction.limit(80)).command);
    }

    @Test
    void overwriteControlTogglesLikeOtherIndependentOptions() {
        QuickBuildUiState base = state(true, QuickBuildUiMode.BUILD, QuickBuildUiShape.CHAIN);
        QuickBuildUiControl overwrite = new QuickBuildUiControl(
                QuickBuildUiControl.Id.OVERWRITE, "Overwrite", false, true);
        QuickBuildUiState state = new QuickBuildUiState(
                base.open, base.mode, base.destroyEnabled, base.destroyDisabledReason,
                base.buildShape, base.destroyShape, base.shapes,
                Arrays.asList(base.controls.get(0), base.controls.get(1), overwrite),
                base.chainLimit, base.chainMinimum, base.chainMaximum,
                base.progressCompleted, base.progressTotal, base.remainingBlocks,
                base.progressText, base.costText, base.selectedItemId, base.missingBlocks,
                base.hintKey, base.confirmKeyLabel, base.dimensions);

        QuickBuildUiTransition result = QuickBuildUiReducer.apply(
                state, QuickBuildUiAction.control(QuickBuildUiControl.Id.OVERWRITE));
        assertTrue(result.state.control(QuickBuildUiControl.Id.OVERWRITE).selected);
        assertTrue(result.state.control(QuickBuildUiControl.Id.FILL).selected);
    }

    @Test
    void conveniencePageKeepsToolAndClampsTreeGroupLimit() {
        QuickBuildUiState base = state(true, QuickBuildUiMode.DESTROY, QuickBuildUiShape.BOX);
        QuickBuildUiTransition page = QuickBuildUiReducer.apply(
                base, QuickBuildUiAction.catalog(QuickBuildUiCatalogPage.CONVENIENCE_TOOLS));
        assertTrue(page.state.convenienceMode());

        QuickBuildUiTransition tool = QuickBuildUiReducer.apply(
                page.state, QuickBuildUiAction.convenienceTool(QuickBuildUiConvenienceTool.TREE_FELL));
        QuickBuildUiTransition limit = QuickBuildUiReducer.apply(
                tool.state, QuickBuildUiAction.convenienceParameter(
                        QuickBuildUiConvenienceParameter.TREE_MAX_BLOCKS, 99_999));
        assertEquals(QuickBuildUiConvenienceTool.TREE_FELL, limit.state.convenienceTool);
        assertEquals(QuickBuildUiConvenienceSettings.TREE_MAX,
                limit.state.convenienceSettings.treeMaxBlocks());
        assertEquals(QuickBuildUiShape.BOX, limit.state.destroyShape);
    }

    @Test
    void smartFillParametersOnlyChangeInSmartFillMode() {
        QuickBuildUiState smart = state(true, QuickBuildUiMode.SMART_FILL, QuickBuildUiShape.BLOCK);
        QuickBuildUiTransition maxBlocks = QuickBuildUiReducer.apply(
                smart, QuickBuildUiAction.smartFillMaxBlocks(99_999));
        QuickBuildUiTransition diameter = QuickBuildUiReducer.apply(
                maxBlocks.state, QuickBuildUiAction.smartFillDiameter(1));
        assertEquals(1024, diameter.state.smartFillMaxBlocks);
        assertEquals(3, diameter.state.smartFillDiameter);

        QuickBuildUiState build = state(true, QuickBuildUiMode.BUILD, QuickBuildUiShape.BLOCK);
        assertEquals(QuickBuildUiTransition.Command.NONE,
                QuickBuildUiReducer.apply(
                        build, QuickBuildUiAction.smartFillMaxBlocks(32)).command);
    }

    @Test
    void buildCatalogOpensSmartFillAsItsFirstToolAndReturnsToShapes() {
        QuickBuildUiState build = state(
                true, QuickBuildUiMode.BUILD, QuickBuildUiShape.BLOCK);
        QuickBuildUiTransition tools = QuickBuildUiReducer.apply(
                build,
                QuickBuildUiAction.catalog(
                        QuickBuildUiCatalogPage.CONVENIENCE_TOOLS));
        assertEquals(QuickBuildUiTransition.Command.SELECT_CATALOG_PAGE,
                tools.command);
        assertEquals(QuickBuildUiMode.SMART_FILL, tools.state.mode);
        assertEquals(QuickBuildUiCatalogPage.CONVENIENCE_TOOLS,
                tools.state.catalogPage);

        QuickBuildUiTransition shapes = QuickBuildUiReducer.apply(
                tools.state,
                QuickBuildUiAction.catalog(QuickBuildUiCatalogPage.SHAPES));
        assertEquals(QuickBuildUiMode.BUILD, shapes.state.mode);
        assertEquals(QuickBuildUiCatalogPage.SHAPES,
                shapes.state.catalogPage);
    }

    private static QuickBuildUiState state(boolean destroyEnabled, QuickBuildUiMode mode,
                                           QuickBuildUiShape destroyShape) {
        return new QuickBuildUiState(true, mode, destroyEnabled, "",
                QuickBuildUiShape.BLOCK, destroyShape,
                Arrays.asList(
                        new QuickBuildUiShapeOption(QuickBuildUiShape.CHAIN,
                                destroyShape == QuickBuildUiShape.CHAIN, true, ""),
                        new QuickBuildUiShapeOption(QuickBuildUiShape.BOX,
                                destroyShape == QuickBuildUiShape.BOX, true, "")),
                Arrays.asList(
                        new QuickBuildUiControl(QuickBuildUiControl.Id.FILL, "Fill", true, true),
                        new QuickBuildUiControl(QuickBuildUiControl.Id.HOLLOW, "Hollow", false, true)),
                64, 1, 512, -1, 0, 0, "", "12", "rtsbuilding:test",
                0, "hint", "B", "3x3x3");
    }
}
