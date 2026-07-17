package com.rtsbuilding.rtsbuilding.common.persist;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 集中保存 RTSBuilding 对 NBT UUID 的业务语义。
 *
 * <p>26.1 移除了 CompoundTag 上的 putUUID/getUUID/hasUUID 快捷方法，
 * 改为 Codec 读写。持久化调用者不应各自知道这项版本差异，因此统一经过
 * 这个窄边界。缺失或损坏的数据返回零 UUID，继续保持旧代码“可读取但不
 * 匹配任何真实对象”的安全行为。</p>
 */
public final class RtsNbtCompat {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private RtsNbtCompat() {
    }

    public static void putUuid(CompoundTag tag, String key, UUID value) {
        if (tag != null && key != null && value != null) {
            tag.store(key, UUIDUtil.CODEC, value);
        }
    }

    public static UUID getUuid(CompoundTag tag, String key) {
        if (tag == null || key == null) {
            return NIL_UUID;
        }
        return tag.read(key, UUIDUtil.CODEC).orElse(NIL_UUID);
    }

    public static boolean hasUuid(CompoundTag tag, String key) {
        return tag != null && key != null && tag.read(key, UUIDUtil.CODEC).isPresent();
    }
}
