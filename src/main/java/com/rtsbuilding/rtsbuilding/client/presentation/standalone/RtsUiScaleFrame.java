package com.rtsbuilding.rtsbuilding.client.presentation.standalone;


public record RtsUiScaleFrame(int oldW, int oldH, double scale, Runnable onClose) implements AutoCloseable {

    @Override
    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
