package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyFragmentPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 单个玩家的有界区域破坏分片状态机；只有收齐并通过全局计数检查才产生旧 trace 请求。 */
public final class RtsAreaDestroyFragmentReassembler {
    public static final int MAX_PENDING_REQUESTS = 4;
    public static final long FRAGMENT_TIMEOUT_TICKS = 600L;
    public static final int MAX_COMPLETED_REQUESTS = 32;

    private final Pending[] pending = new Pending[MAX_PENDING_REQUESTS];
    private final long[] completedIds = new long[MAX_COMPLETED_REQUESTS];
    private final long[] completedUntil = new long[MAX_COMPLETED_REQUESTS];
    private int completedSize;

    public synchronized Result accept(C2SRtsAreaDestroyFragmentPayload fragment, long nowTick) {
        expire(nowTick);
        String invalid = validate(fragment);
        if (invalid != null) return Result.rejected(invalid);
        if (isRecentlyCompleted(fragment.requestId(), nowTick)) {
            return Result.duplicate("request already completed");
        }

        int slot = findPending(fragment.requestId());
        Pending request;
        if (slot < 0) {
            slot = firstEmptyPending();
            if (slot < 0) return Result.rejected("too many pending area destroy requests");
            request = new Pending(fragment, nowTick);
            pending[slot] = request;
        } else {
            request = pending[slot];
            String mismatch = request.matches(fragment);
            if (mismatch != null) return reject(slot, mismatch);
        }

        if (fragment.metadataPresent() && request.metadata != null
                && !request.metadata.sameAs(fragment)) {
            return reject(slot, "conflicting area destroy metadata");
        }
        Part incoming = Part.from(fragment);
        Part previous = request.parts[fragment.fragmentIndex()];
        if (previous != null) {
            if (previous.sameAs(incoming)) {
                request.lastSeenTick = nowTick;
                return Result.duplicate("duplicate area destroy fragment");
            }
            return reject(slot, "conflicting duplicate area destroy fragment");
        }
        if (request.receivedTargets > request.totalPositions - incoming.positions.size()) {
            return reject(slot, "area destroy fragment target budget exceeded");
        }
        request.parts[fragment.fragmentIndex()] = incoming;
        request.receivedFragments++;
        request.receivedTargets += incoming.positions.size();
        request.lastSeenTick = nowTick;
        if (fragment.metadataPresent() && request.metadata == null) {
            request.metadata = Metadata.from(fragment);
        }
        if (request.receivedFragments < request.fragmentCount) {
            return Result.incomplete(request.receivedFragments, request.fragmentCount);
        }
        if (request.receivedTargets != request.totalPositions || request.metadata == null) {
            return reject(slot, "area destroy fragments are complete but metadata/targets are incomplete");
        }

        List<BlockPos> positions = new ArrayList<>(request.totalPositions);
        for (Part part : request.parts) {
            if (part == null) return reject(slot, "area destroy fragment index is missing");
            positions.addAll(part.positions);
        }
        if (positions.size() != request.totalPositions) {
            return reject(slot, "area destroy target count does not match fragments");
        }
        Metadata metadata = request.metadata;
        C2SRtsAreaDestroyTracePayload assembled = new C2SRtsAreaDestroyTracePayload(
                request.traceId, request.sequence, request.clientTick, request.heldMs,
                request.inputKind, request.stopOrigin, List.copyOf(positions), metadata.toolSlot,
                metadata.toolItemId, metadata.toolPrototype.copy(), metadata.toolProtectionEnabled);
        pending[slot] = null;
        rememberCompleted(request.requestId, nowTick);
        return Result.complete(assembled, request.fragmentCount);
    }

    public synchronized int expire(long nowTick) {
        int removed = 0;
        for (int i = 0; i < pending.length; i++) {
            Pending value = pending[i];
            if (value == null || nowTick < value.startedTick || nowTick < value.lastSeenTick
                    || (!timedOut(nowTick, value.startedTick) && !timedOut(nowTick, value.lastSeenTick))) {
                continue;
            }
            pending[i] = null;
            removed++;
        }
        for (int i = completedSize - 1; i >= 0; i--) {
            if (nowTick >= completedUntil[i]) removeCompleted(i);
        }
        return removed;
    }

    public synchronized int pendingCount() {
        int count = 0;
        for (Pending value : pending) if (value != null) count++;
        return count;
    }

    public synchronized int pendingTargetCount() {
        int count = 0;
        for (Pending value : pending) if (value != null) count += value.receivedTargets;
        return count;
    }

    public synchronized boolean hasPending(long requestId) {
        return findPending(requestId) >= 0;
    }

    public synchronized boolean isEmpty() {
        return pendingCount() == 0 && completedSize == 0;
    }

    private static boolean timedOut(long nowTick, long sinceTick) {
        return nowTick >= sinceTick && nowTick - sinceTick >= FRAGMENT_TIMEOUT_TICKS;
    }

    private Result reject(int slot, String reason) {
        pending[slot] = null;
        return Result.rejected(reason);
    }

    private int findPending(long requestId) {
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] != null && pending[i].requestId == requestId) return i;
        }
        return -1;
    }

    private int firstEmptyPending() {
        for (int i = 0; i < pending.length; i++) if (pending[i] == null) return i;
        return -1;
    }

    private boolean isRecentlyCompleted(long requestId, long nowTick) {
        for (int i = 0; i < completedSize; i++) {
            if (completedIds[i] != requestId) continue;
            if (nowTick < completedUntil[i]) return true;
            removeCompleted(i);
            return false;
        }
        return false;
    }

    private void rememberCompleted(long requestId, long nowTick) {
        if (completedSize == MAX_COMPLETED_REQUESTS) {
            int oldest = 0;
            for (int i = 1; i < completedSize; i++) {
                if (completedUntil[i] < completedUntil[oldest]) oldest = i;
            }
            removeCompleted(oldest);
        }
        completedIds[completedSize] = requestId;
        completedUntil[completedSize] = nowTick + FRAGMENT_TIMEOUT_TICKS;
        completedSize++;
    }

    private void removeCompleted(int index) {
        int last = --completedSize;
        if (index != last) {
            completedIds[index] = completedIds[last];
            completedUntil[index] = completedUntil[last];
        }
    }

    private static String validate(C2SRtsAreaDestroyFragmentPayload fragment) {
        if (fragment == null || fragment.requestId() == 0L) return "request identity is invalid";
        if (fragment.fragmentCount() < 1 || fragment.fragmentCount() > C2SRtsAreaDestroyFragmentPayload.MAX_FRAGMENTS
                || fragment.fragmentIndex() < 0 || fragment.fragmentIndex() >= fragment.fragmentCount()) {
            return "fragment index/count is invalid";
        }
        if (fragment.totalPositions() < 1 || fragment.totalPositions() > MiningLimits.MAX_VOLUME
                || fragment.fragmentCount() > fragment.totalPositions()) return "target budget is invalid";
        List<BlockPos> positions = fragment.positions();
        if (positions == null || positions.isEmpty()
                || positions.size() > C2SRtsAreaDestroyFragmentPayload.MAX_POSITIONS_PER_FRAGMENT
                || positions.size() > fragment.totalPositions()) return "fragment target count is invalid";
        if (!fragment.metadataPresent() && (!fragment.toolItemId().isEmpty()
                || !fragment.toolPrototype().isEmpty() || fragment.toolProtectionEnabled())) {
            return "metadata flag is invalid";
        }
        if (fragment.metadataPresent() && (fragment.toolSlot() < 0 || fragment.toolSlot() > 8)) {
            return "tool slot is invalid";
        }
        return null;
    }

    public record Result(Status status, C2SRtsAreaDestroyTracePayload payload,
                         String reason, int receivedFragments, int expectedFragments) {
        static Result incomplete(int received, int expected) {
            return new Result(Status.INCOMPLETE, null, "", received, expected);
        }

        static Result complete(C2SRtsAreaDestroyTracePayload payload, int count) {
            return new Result(Status.COMPLETE, payload, "", count, count);
        }

        static Result duplicate(String reason) {
            return new Result(Status.DUPLICATE, null, reason, 0, 0);
        }

        static Result rejected(String reason) {
            return new Result(Status.REJECTED, null, reason, 0, 0);
        }
    }

    public enum Status { INCOMPLETE, COMPLETE, DUPLICATE, REJECTED }

    private static final class Pending {
        private final long requestId;
        private final long traceId;
        private final int sequence;
        private final long clientTick;
        private final int heldMs;
        private final byte inputKind;
        private final byte stopOrigin;
        private final int fragmentCount;
        private final int totalPositions;
        private final Part[] parts;
        private final long startedTick;
        private long lastSeenTick;
        private Metadata metadata;
        private int receivedFragments;
        private int receivedTargets;

        private Pending(C2SRtsAreaDestroyFragmentPayload fragment, long nowTick) {
            requestId = fragment.requestId();
            traceId = fragment.traceId();
            sequence = fragment.sequence();
            clientTick = fragment.clientTick();
            heldMs = Math.max(0, fragment.heldMs());
            inputKind = fragment.inputKind();
            stopOrigin = fragment.stopOrigin();
            fragmentCount = fragment.fragmentCount();
            totalPositions = fragment.totalPositions();
            parts = new Part[fragmentCount];
            startedTick = nowTick;
            lastSeenTick = nowTick;
        }

        private String matches(C2SRtsAreaDestroyFragmentPayload fragment) {
            if (fragment.fragmentCount() != fragmentCount) return "fragment count changed within request";
            if (fragment.totalPositions() != totalPositions) return "target count changed within request";
            if (fragment.traceId() != traceId || fragment.sequence() != sequence
                    || fragment.clientTick() != clientTick || Math.max(0, fragment.heldMs()) != heldMs
                    || fragment.inputKind() != inputKind || fragment.stopOrigin() != stopOrigin) {
                return "trace metadata changed within request";
            }
            return null;
        }
    }

    private record Part(boolean metadataPresent, List<BlockPos> positions) {
        private static Part from(C2SRtsAreaDestroyFragmentPayload fragment) {
            return new Part(fragment.metadataPresent(), List.copyOf(fragment.positions()));
        }

        private boolean sameAs(Part other) {
            return metadataPresent == other.metadataPresent && positions.equals(other.positions);
        }
    }

    private record Metadata(byte toolSlot, String toolItemId, ItemStack toolPrototype,
                            boolean toolProtectionEnabled) {
        private static Metadata from(C2SRtsAreaDestroyFragmentPayload fragment) {
            return new Metadata(fragment.toolSlot(), fragment.toolItemId(),
                    fragment.toolPrototype().copy(), fragment.toolProtectionEnabled());
        }

        private boolean sameAs(C2SRtsAreaDestroyFragmentPayload fragment) {
            return toolSlot == fragment.toolSlot() && Objects.equals(toolItemId, fragment.toolItemId())
                    && toolProtectionEnabled == fragment.toolProtectionEnabled()
                    && ItemStack.matches(toolPrototype, fragment.toolPrototype());
        }
    }
}
