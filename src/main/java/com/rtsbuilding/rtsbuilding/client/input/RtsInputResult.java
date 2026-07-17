package com.rtsbuilding.rtsbuilding.client.input;

/**
 * 新 UI 路由的强语义结果。旧面板仍可通过 {@link #blocksFurtherInput()} 适配为 boolean。
 */
public enum RtsInputResult {
    PASS(false),
    CONSUMED(true),
    CAPTURE_POINTER(true),
    BLOCK_WORLD(true);

    private final boolean blocksFurtherInput;

    RtsInputResult(boolean blocksFurtherInput) {
        this.blocksFurtherInput = blocksFurtherInput;
    }

    public boolean blocksFurtherInput() {
        return blocksFurtherInput;
    }
}
