package com.rtsbuilding.rtsbuilding.server.task.buffer;

import com.rtsbuilding.rtsbuilding.server.task.BufferDrainTaskPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** BufferDrainTaskPayload 的版本化、有界 NBT 编解码器。 */
public final class BufferEscrowCodec {
    public static final int SCHEMA_VERSION = 2;

    private BufferEscrowCodec() {
    }

    public static CompoundTag encode(BufferDrainTaskPayload payload, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", SCHEMA_VERSION);
        root.putUUID("owner", payload.ownerId());
        root.putUUID("escrow", payload.escrowId());
        if (payload.legacySourceFingerprint() != null) {
            root.putString("legacy_source_fingerprint", payload.legacySourceFingerprint().sha256());
        }
        root.putString("dimension", payload.dimension().location().toString());
        root.putLong("queued_at", payload.state().firstQueuedGameTime());
        ListTag encodedEntries = new ListTag();
        for (BufferEscrowEntry entry : payload.state().entries()) {
            CompoundTag encoded = new CompoundTag();
            encoded.putUUID("claim", entry.claimId());
            encoded.putInt("ordinal", entry.ordinal());
            if (entry.sourceEntityId() != null) encoded.putUUID("source", entry.sourceEntityId());
            encoded.put("source_stack", entry.sourceSnapshot().save(registries));
            ItemStack owned = entry.ownedStack();
            if (!owned.isEmpty()) encoded.put("owned_stack", owned.save(registries));
            encoded.putString("phase", entry.phase().name());
            if (entry.attemptId() != null) encoded.putUUID("attempt", entry.attemptId());
            encoded.putInt("reserved", entry.reservedCount());
            encoded.putString("recovery", entry.recoveryCode().name());
            encodedEntries.add(encoded);
        }
        root.put("entries", encodedEntries);
        return root;
    }

    public static BufferDrainTaskPayload decode(CompoundTag root, HolderLookup.Provider registries) {
        int schema = root == null || !root.contains("schema", Tag.TAG_INT)
                ? -1 : root.getInt("schema");
        if (root == null
                || (schema != 1 && schema != SCHEMA_VERSION)
                || !root.hasUUID("owner")
                || !root.hasUUID("escrow")
                || !root.contains("dimension", Tag.TAG_STRING)
                || !root.contains("queued_at", Tag.TAG_LONG)
                || !root.contains("entries", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("不支持或不完整的 buffer escrow payload");
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(root.getString("dimension"));
        if (dimensionId == null || !dimensionId.toString().equals(root.getString("dimension"))) {
            throw new IllegalArgumentException("buffer escrow 维度无效");
        }
        ListTag encodedEntries = root.getList("entries", Tag.TAG_COMPOUND);
        if (encodedEntries.size() > BufferEscrowState.MAX_STACKS) {
            throw new IllegalArgumentException("buffer escrow stack 数量越界");
        }
        List<BufferEscrowEntry> entries = new ArrayList<>(encodedEntries.size());
        for (int i = 0; i < encodedEntries.size(); i++) {
            CompoundTag encoded = encodedEntries.getCompound(i);
            requireEntryFields(encoded);
            ItemStack source = ItemStack.parseOptional(registries, encoded.getCompound("source_stack"));
            ItemStack owned = encoded.contains("owned_stack", Tag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(registries, encoded.getCompound("owned_stack"))
                    : ItemStack.EMPTY;
            BufferEscrowPhase phase = parseEnum(BufferEscrowPhase.class, encoded.getString("phase"));
            BufferRecoveryCode recovery = parseEnum(BufferRecoveryCode.class, encoded.getString("recovery"));
            entries.add(new BufferEscrowEntry(
                    encoded.getUUID("claim"),
                    encoded.getInt("ordinal"),
                    encoded.hasUUID("source") ? encoded.getUUID("source") : null,
                    source,
                    owned,
                    phase,
                    encoded.hasUUID("attempt") ? encoded.getUUID("attempt") : null,
                    encoded.getInt("reserved"),
                    recovery));
        }
        // schema 1 的世界实体任务始终保留 source UUID，可以安全继续；早期 legacy Session
        // 快照没有 source 也没有来源指纹，无法证明它与 Session shadow 谁拥有物品，只能保留证据并停住。
        if (schema == 1 && !entries.isEmpty()
                && entries.stream().allMatch(entry -> entry.sourceEntityId() == null)) {
            entries = entries.stream()
                    .map(entry -> entry.phase() == BufferEscrowPhase.RECOVERY_REQUIRED
                            ? entry
                            : entry.recoveryRequired(BufferRecoveryCode.LEGACY_OWNERSHIP_UNPROVEN))
                    .toList();
        }
        BufferEscrowState state = new BufferEscrowState(root.getLong("queued_at"), entries);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new BufferDrainTaskPayload(
                root.getUUID("owner"), dimension, root.getUUID("escrow"),
                root.contains("legacy_source_fingerprint", Tag.TAG_STRING)
                        ? new LegacyBufferSourceFingerprint(root.getString("legacy_source_fingerprint"))
                        : null,
                state);
    }

    private static void requireEntryFields(CompoundTag entry) {
        if (!entry.hasUUID("claim")
                || !entry.contains("ordinal", Tag.TAG_INT)
                || !entry.contains("source_stack", Tag.TAG_COMPOUND)
                || !entry.contains("phase", Tag.TAG_STRING)
                || !entry.contains("reserved", Tag.TAG_INT)
                || !entry.contains("recovery", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("不完整的 buffer escrow entry");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知的 buffer escrow 枚举值: " + value, exception);
        }
    }
}
