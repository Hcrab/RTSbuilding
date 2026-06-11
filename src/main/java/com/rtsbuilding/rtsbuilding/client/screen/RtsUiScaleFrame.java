package com.rtsbuilding.rtsbuilding.client.screen;


/**
 * RTS UI 缂╂斁甯х鐞嗐€?
 * <p>
 * 鐢ㄤ簬鍦ㄧ缉鏀炬覆鏌?杈撳叆澶勭悊鏃朵复鏃朵慨鏀瑰睆骞曞昂瀵革紝
 * 鍦?{@link #close()} 涓嚜鍔ㄦ仮澶嶅師濮嬪昂瀵搞€?
 */
record RtsUiScaleFrame(int oldW, int oldH, double scale, Runnable onClose) implements AutoCloseable {

    @Override
    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
