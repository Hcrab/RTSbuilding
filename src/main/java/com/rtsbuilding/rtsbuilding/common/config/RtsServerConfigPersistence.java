package com.rtsbuilding.rtsbuilding.common.config;

/**
 * Forge/NeoForge 共用的配置保存事务边界：候选值失败后恢复内存，并再次使用 loader save
 * 入口写回旧值；它不拥有文件和线程，便于注入保存前、写盘后异常回归。
 */
public final class RtsServerConfigPersistence {
    @FunctionalInterface
    public interface SaveOperation { void run(); }
    @FunctionalInterface
    public interface RestoreOperation { void run(); }
    public record Result(boolean committed, boolean rollbackCompleted, RuntimeException failure) {
        public Result {
            if (committed && failure != null) throw new IllegalArgumentException("a committed save cannot carry a failure");
            if (failure == null && !committed) throw new IllegalArgumentException("a failed save must carry its failure");
        }
    }
    private RtsServerConfigPersistence() { }
    /** 保存候选值；失败时先恢复内存，再以同一 loader 入口保存旧值。 */
    public static Result saveWithRollback(SaveOperation saveCandidate, RestoreOperation restoreMemory, SaveOperation savePrevious) {
        if (saveCandidate == null || restoreMemory == null || savePrevious == null) throw new IllegalArgumentException("save transaction operations must be non-null");
        try { saveCandidate.run(); return new Result(true, false, null); }
        catch (RuntimeException failure) {
            boolean memoryRestored = true;
            try { restoreMemory.run(); } catch (RuntimeException restoreFailure) { memoryRestored = false; failure.addSuppressed(restoreFailure); }
            try { savePrevious.run(); return new Result(false, memoryRestored, failure); }
            catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); return new Result(false, false, failure); }
        }
    }
}
