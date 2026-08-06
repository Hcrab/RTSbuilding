package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/**
 * 蓝图材料窗口与命名窗口的纯 Java 状态。
 *
 * <p>本类只管理窗口开关、文本草稿和待重命名条目，不访问文件、世界或
 * Minecraft 控件。文件名清洗、保存、重命名和状态提示仍由生产适配器负责，
 * 因而同一状态机可以直接复用到 Forge 1.20.1 和后续回移版本。</p>
 */
public final class BlueprintDialogState<T> {
    public static final int MAX_NAME_LENGTH = 80;

    private boolean materialOpen;
    private int materialScroll;
    private NameMode nameMode = NameMode.NONE;
    private String nameValue = "";
    private T nameEntry;
    private boolean replaceOnFirstInput;
    private long captureBlockCount;

    public boolean isMaterialOpen() {
        return this.materialOpen;
    }

    public int materialScroll() {
        return this.materialScroll;
    }

    public void openMaterial() {
        this.materialOpen = true;
        this.materialScroll = 0;
    }

    public void setMaterialScroll(int scroll) {
        this.materialScroll = Math.max(0, scroll);
    }

    public void closeMaterial() {
        this.materialOpen = false;
        this.materialScroll = 0;
    }

    public boolean isNameOpen() {
        return this.nameMode != NameMode.NONE;
    }

    public boolean isCaptureNameOpen() {
        return this.nameMode == NameMode.CAPTURE_SAVE;
    }

    public String nameValue() {
        return this.nameValue;
    }

    public T nameEntry() {
        return this.nameEntry;
    }

    public boolean replaceOnFirstInput() {
        return this.replaceOnFirstInput;
    }

    public long captureBlockCount() {
        return this.captureBlockCount;
    }

    public void setNameValue(String value) {
        if (!isNameOpen()) {
            return;
        }
        String safe = value == null ? "" : value;
        this.nameValue = safe.substring(
                0, Math.min(MAX_NAME_LENGTH, safe.length()));
        this.replaceOnFirstInput = false;
    }

    public void openCaptureName(String initialValue, long blockCount) {
        this.nameMode = NameMode.CAPTURE_SAVE;
        this.nameValue = safe(initialValue);
        this.nameEntry = null;
        this.replaceOnFirstInput = false;
        this.captureBlockCount = Math.max(0L, blockCount);
        closeMaterial();
    }

    public void openRename(String initialValue, T entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry");
        }
        this.nameMode = NameMode.RENAME_ENTRY;
        this.nameValue = safe(initialValue);
        this.nameEntry = entry;
        this.replaceOnFirstInput = true;
        this.captureBlockCount = 0L;
        closeMaterial();
    }

    public NameMode cancelName() {
        NameMode previous = this.nameMode;
        clearName();
        return previous;
    }

    public Confirmation<T> consumeName() {
        if (!isNameOpen()) {
            return null;
        }
        Confirmation<T> result = new Confirmation<T>(
                this.nameMode, this.nameValue, this.nameEntry);
        clearName();
        return result;
    }

    public void clearAll() {
        closeMaterial();
        clearName();
    }

    public void clearName() {
        this.nameMode = NameMode.NONE;
        this.nameValue = "";
        this.nameEntry = null;
        this.replaceOnFirstInput = false;
        this.captureBlockCount = 0L;
    }

    private static String safe(String value) {
        String safe = value == null ? "" : value;
        return safe.substring(0, Math.min(MAX_NAME_LENGTH, safe.length()));
    }

    public enum NameMode {
        NONE,
        CAPTURE_SAVE,
        RENAME_ENTRY
    }

    /** 一次确认动作的不可变快照，消费后窗口状态立即清空。 */
    public static final class Confirmation<T> {
        private final NameMode mode;
        private final String value;
        private final T entry;

        private Confirmation(NameMode mode, String value, T entry) {
            this.mode = mode;
            this.value = value;
            this.entry = entry;
        }

        public NameMode mode() {
            return this.mode;
        }

        public String value() {
            return this.value;
        }

        public T entry() {
            return this.entry;
        }
    }
}
