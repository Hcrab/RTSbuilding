package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.NBTTagCompound;

import java.nio.file.Path;

/**
 * DataCluster 使用的最小持久化端口。
 *
 * <p>该接口只描述一次完整快照的读取和替换，不负责组件编解码、脏标记或重试策略。
 * 生产环境由 {@link RtsAtomicNbtStore} 实现；独立端口也让故障注入测试不依赖具体文件系统行为。
 */
public interface RtsNbtStore {

    ReadResult readResult();

    boolean write(NBTTagCompound tag);

    String label();

    /** 可选的物理路径，仅供持久层统计写入字节数；内存测试实现可以返回 null。 */
    default Path path() {
        return null;
    }

    /** 明确区分文件不存在、读取成功和读取失败，避免损坏文件被当作空存档覆盖。 */
    interface ReadResult {

        static ReadResult found(NBTTagCompound root) {
            return new Found(root);
        }

        static ReadResult missing() {
            return Missing.INSTANCE;
        }

        static ReadResult failed(Throwable cause) {
            return new Failed(cause);
        }

        final class Found implements ReadResult {
            private final NBTTagCompound root;

            public Found(NBTTagCompound root) {
                if (root == null) throw new IllegalArgumentException("root 不能为 null");
                this.root = root;
            }

            public NBTTagCompound root() {
                return root;
            }
        }

        final class Missing implements ReadResult {
            private static final Missing INSTANCE = new Missing();

            private Missing() {
            }
        }

        final class Failed implements ReadResult {
            private final Throwable cause;

            public Failed(Throwable cause) {
                if (cause == null) throw new IllegalArgumentException("cause 不能为 null");
                this.cause = cause;
            }

            public Throwable cause() {
                return cause;
            }
        }
    }
}
