package com.rtsbuilding.rtsbuilding.common.config;

/**
 * 服务端配置的小型保存事务边界。
 *
 * <p>loader 的 {@code save()} 可能在写盘后才因 Reloading 回调异常返回失败，因此配置层
 * 必须在报告失败前把内存恢复并再次通过同一个 loader 保存旧值。这个类不拥有文件、锁或
 * 线程，只把顺序做成可注入的纯边界，方便测试“写盘前失败”和“写盘后回调失败”。</p>
 */
public final class RtsServerConfigPersistence {
    @FunctionalInterface
    public interface SaveOperation {
        void run();
    }

    @FunctionalInterface
    public interface RestoreOperation {
        void run();
    }

    public record Result(boolean committed, boolean rollbackCompleted, RuntimeException failure) {
        public Result {
            if (committed && failure != null) {
                throw new IllegalArgumentException("a committed save cannot carry a failure");
            }
            if (failure == null && !committed) {
                throw new IllegalArgumentException("a failed save must carry its failure");
            }
        }
    }

    private RtsServerConfigPersistence() {
    }

    /**
     * 先保存候选值；候选保存出现任何异常时恢复内存，并用同一个持久化入口保存旧值。
     * 旧值保存也失败时将第二个异常挂到原始异常上，调用者不得把结果当作成功。
     */
    public static Result saveWithRollback(SaveOperation saveCandidate,
            RestoreOperation restoreMemory, SaveOperation savePrevious) {
        if (saveCandidate == null || restoreMemory == null || savePrevious == null) {
            throw new IllegalArgumentException("save transaction operations must be non-null");
        }
        try {
            saveCandidate.run();
            return new Result(true, false, null);
        } catch (RuntimeException failure) {
            boolean memoryRestored = true;
            try {
                restoreMemory.run();
            } catch (RuntimeException restoreFailure) {
                memoryRestored = false;
                failure.addSuppressed(restoreFailure);
            }
            try {
                savePrevious.run();
                return new Result(false, memoryRestored, failure);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                return new Result(false, false, failure);
            }
        }
    }
}
