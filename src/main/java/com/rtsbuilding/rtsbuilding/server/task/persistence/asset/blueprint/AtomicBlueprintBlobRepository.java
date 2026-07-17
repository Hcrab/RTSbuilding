package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每任务独占蓝图 blob 的原子 write-once 仓库。
 *
 * <p>该仓库不读 Player/Level/Session，也不发布 TaskSnapshot。正确顺序由上层保证：后台先成功 writeOnce，
 * 主线程再原子提交 manifest metadata + 轻量 task。相同 ID 的完全相同内容可幂等重试；相同 ID 的不同 hash
 * 必须 fail-closed，绝不覆盖。</p>
 */
public final class AtomicBlueprintBlobRepository {
    public static final int MAX_SCAN_ENTRIES = 100_000;
    public static final long MAX_SCAN_COMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L;
    public static final int MAX_RECONCILE_ENTRIES = 200_000;
    private static final int LOCK_STRIPES = 256;
    private static final Object[] ASSET_LOCKS = createLockStripes();

    private final Path directory;
    private final BlueprintBlobCodec codec;
    private final AtomicMover atomicMover;
    private final Object receiptIssuer = new Object();
    private final ConcurrentHashMap<TaskAssetId, Integer> admissionPins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TaskAssetId, Integer> activeRootLeases = new ConcurrentHashMap<>();
    private volatile boolean writeAdmissionReadOnly;
    private volatile Set<TaskAssetId> maintenanceLiveAssetIds;

    public AtomicBlueprintBlobRepository(MinecraftServer server, BlueprintBlobCodec codec) {
        this(Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve("rtsbuilding/task_blobs/blueprint"), codec, AtomicBlueprintBlobRepository::atomicMove);
    }

    /** 包内文件入口仅用于故障注入测试。 */
    AtomicBlueprintBlobRepository(Path directory, BlueprintBlobCodec codec) {
        this(directory, codec, AtomicBlueprintBlobRepository::atomicMove);
    }

    /** 仅供故障注入测试验证“不支持原子移动时绝不降级”。 */
    AtomicBlueprintBlobRepository(Path directory, BlueprintBlobCodec codec, AtomicMover atomicMover) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.atomicMover = Objects.requireNonNull(atomicMover, "atomicMover");
    }

    public WriteOutcome writeOnce(BlueprintBlobRecord record) {
        Objects.requireNonNull(record, "record");
        synchronized (assetLock(record.assetId())) {
            return writeOnceLocked(record);
        }
    }

    /**
     * 完成 write-once、force 和回读校验后签发不可由调用方构造的 durable receipt。
     * 上层只能使用同一个仓库实例签发的 receipt 建立 task root 引用。
     */
    public DurableBlueprintBlobReceipt writeDurably(BlueprintBlobRecord record) {
        WriteOutcome outcome = writeOnce(record);
        LoadResult.Found found = requireFoundResult(load(record.assetId()));
        requireSame(found.record(), record);
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                record.assetId(), record.taskId(), "blueprint", record.sha256(),
                found.compressedBytes(), codec.logicalBytes(record));
        return new DurableBlueprintBlobReceipt(receiptIssuer, metadata, outcome);
    }

    /** 拒绝另一世界/另一仓库实例签发或伪造的 receipt。 */
    public VerifiedDurableBlueprintBlob requireIssued(DurableBlueprintBlobReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        if (receipt.issuer != receiptIssuer) {
            throw new BlobRepositoryException("蓝图 blob receipt 不属于当前仓库实例");
        }
        TaskAssetMetadata metadata = receipt.metadata;
        LoadResult.Found found = requireFoundResult(load(metadata.assetId()));
        BlueprintBlobRecord record = found.record();
        if (!record.taskId().equals(metadata.taskId())
                || !record.sha256().equals(metadata.sha256())
                || found.compressedBytes() != metadata.compressedBytes()
                || codec.logicalBytes(record) != metadata.logicalBytes()) {
            throw new BlobRepositoryException("蓝图 blob receipt 对应的磁盘实体已经变化: " + metadata.assetId());
        }
        return new VerifiedDurableBlueprintBlob(metadata, record.blockCount());
    }

    /** 后台 writer 回读完成后签发主线程可 O(1) 验证的 admission proof。 */
    public DurableBlueprintAdmissionProof verifyForAdmission(DurableBlueprintBlobReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        TaskAssetId assetId = receipt.metadata.assetId();
        synchronized (assetLock(assetId)) {
            VerifiedDurableBlueprintBlob verified = requireIssued(receipt);
            admissionPins.merge(assetId, 1, Integer::sum);
            return new DurableBlueprintAdmissionProof(receiptIssuer, verified, receipt.outcome);
        }
    }

    /** 主线程只验证 proof 的仓库实例与 pin，不重新读取或解压大型 blob。 */
    public VerifiedDurableBlueprintBlob requireIssued(DurableBlueprintAdmissionProof proof) {
        TaskAssetId assetId = requireProofOwner(proof);
        synchronized (assetLock(assetId)) {
            requireActiveProofLocked(proof, assetId);
            return proof.verified;
        }
    }

    /** 仅用于已存在 durable root 的幂等提交；新 root 必须在 ACK 后调用 promote。 */
    public void consumeAdmissionProof(DurableBlueprintAdmissionProof proof) {
        releaseAdmissionProof(proof, ProofRelease.CONSUME);
    }

    /** root 已精确 ACK 后把短期 admission pin 晋升为 active-root lease。 */
    public void promoteAdmissionProof(DurableBlueprintAdmissionProof proof) {
        releaseAdmissionProof(proof, ProofRelease.PROMOTE_TO_ROOT);
    }

    /** 永久拒绝尚未建立 reservation 的请求；仅精确回收本次新写出的 blob。 */
    public void rejectAdmissionProof(DurableBlueprintAdmissionProof proof) {
        releaseAdmissionProof(proof, ProofRelease.REJECT);
    }

    private void releaseAdmissionProof(DurableBlueprintAdmissionProof proof, ProofRelease release) {
        TaskAssetId assetId = requireProofOwner(proof);
        synchronized (assetLock(assetId)) {
            requireActiveProofLocked(proof, assetId);
            if (release == ProofRelease.CONSUME && !activeRootLeases.containsKey(assetId)) {
                throw new BlobRepositoryException(
                        "没有 active root lease 时不得直接消费 admission proof: " + assetId);
            }
            proof.active = false;
            admissionPins.compute(assetId, (ignored, count) -> {
                if (count == null || count <= 0) {
                    throw new BlobRepositoryException("蓝图 admission pin 计数损坏: " + assetId);
                }
                return count == 1 ? null : count - 1;
            });
            if (release == ProofRelease.PROMOTE_TO_ROOT) {
                activeRootLeases.merge(assetId, 1, Integer::sum);
            } else if (release == ProofRelease.REJECT && proof.outcome == WriteOutcome.WRITTEN
                    && !admissionPins.containsKey(assetId)
                    && !activeRootLeases.containsKey(assetId)) {
                deleteIfMatchesLocked(assetId, proof.verified.metadata().sha256());
            }
        }
    }

    private TaskAssetId requireProofOwner(DurableBlueprintAdmissionProof proof) {
        Objects.requireNonNull(proof, "proof");
        if (proof.issuer != receiptIssuer) {
            throw new BlobRepositoryException("蓝图 admission proof 不属于当前仓库实例");
        }
        return proof.verified.metadata().assetId();
    }

    private void requireActiveProofLocked(
            DurableBlueprintAdmissionProof proof, TaskAssetId assetId) {
        if (!proof.active || !admissionPins.containsKey(assetId)) {
            throw new BlobRepositoryException("蓝图 admission proof 已消费或失效: " + assetId);
        }
    }

    /** 新资产在启动 orphan 回收结束前保持只读，避免被旧 root 保护集误删。 */
    public boolean reconciliationInProgress() {
        return maintenanceLiveAssetIds != null;
    }

    private WriteOutcome writeOnceLocked(BlueprintBlobRecord record) {
        Set<TaskAssetId> maintenanceLive = maintenanceLiveAssetIds;
        if (maintenanceLive != null && !maintenanceLive.contains(record.assetId())) {
            throw new BlobRepositoryException("blob 目录正在按启动 root 维护，暂不接纳新蓝图资产");
        }
        Path target = path(record.assetId());
        if (Files.exists(target)) {
            BlueprintBlobRecord existing = requireFound(load(record.assetId()));
            requireSame(existing, record);
            forceFile(target);
            forceDirectoryBestEffort(directory);
            return WriteOutcome.ALREADY_PRESENT;
        }
        if (writeAdmissionReadOnly) {
            throw new BlobRepositoryException("blob 目录扫描已超过维护配额，拒绝接纳新蓝图资产");
        }

        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(directory);
            writeAndForceNew(temporary, record);
            long compressedBytes = Files.size(temporary);
            if (compressedBytes <= 0L || compressedBytes > BlueprintBlobCodec.MAX_COMPRESSED_BYTES) {
                throw new IOException("blob 压缩文件大小越界: " + compressedBytes);
            }
            try (var input = new BufferedInputStream(Files.newInputStream(temporary))) {
                requireSame(codec.decodeCompressed(input, compressedBytes), record);
            }
            if (!moveNew(temporary, target, atomicMover)) {
                BlueprintBlobRecord existing = requireFound(load(record.assetId()));
                requireSame(existing, record);
                forceFile(target);
                forceDirectoryBestEffort(directory);
                return WriteOutcome.ALREADY_PRESENT;
            }
            forceFile(target);
            forceDirectoryBestEffort(directory);
            BlueprintBlobRecord verified = requireFound(load(record.assetId()));
            requireSame(verified, record);
            return WriteOutcome.WRITTEN;
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw new BlobRepositoryException("原子写入蓝图 blob 失败: " + record.assetId(), failure);
        }
    }

    public LoadResult load(TaskAssetId assetId) {
        Objects.requireNonNull(assetId, "assetId");
        Path file = path(assetId);
        if (!Files.exists(file)) return new LoadResult.Missing();
        try {
            if (!Files.isRegularFile(file)) throw new IOException("blob 路径不是普通文件");
            long compressedBytes = Files.size(file);
            if (compressedBytes <= 0L || compressedBytes > BlueprintBlobCodec.MAX_COMPRESSED_BYTES) {
                throw new IOException("blob 压缩文件大小越界: " + compressedBytes);
            }
            BlueprintBlobRecord record;
            try (var input = new BufferedInputStream(Files.newInputStream(file))) {
                record = codec.decodeCompressed(input, compressedBytes);
            }
            if (!record.assetId().equals(assetId)) throw new IOException("blob 文件名与内容 ID 不一致");
            return new LoadResult.Found(record, compressedBytes);
        } catch (IOException | RuntimeException failure) {
            return new LoadResult.Failed(failure);
        }
    }

    /** 仅在 manifest 已 durable 移除 metadata/建立 tombstone 后调用；hash 不符时拒绝误删。 */
    public boolean deleteIfMatches(TaskAssetId assetId, String sha256) {
        Objects.requireNonNull(assetId, "assetId");
        synchronized (assetLock(assetId)) {
            return deleteIfMatchesLocked(assetId, sha256);
        }
    }

    private boolean deleteIfMatchesLocked(TaskAssetId assetId, String sha256) {
        if (admissionPins.containsKey(assetId)) {
            throw new BlobRepositoryException("蓝图 blob 正被 admission proof 固定，拒绝删除: " + assetId);
        }
        if (activeRootLeases.containsKey(assetId)) {
            throw new BlobRepositoryException("蓝图 blob 仍被 active root lease 引用，拒绝删除: " + assetId);
        }
        LoadResult loaded = load(assetId);
        if (loaded instanceof LoadResult.Missing) return false;
        BlueprintBlobRecord record = requireFound(loaded);
        if (!record.sha256().equals(sha256)) {
            throw new BlobRepositoryException("拒绝删除 hash 不匹配的蓝图 blob: " + assetId);
        }
        try {
            return Files.deleteIfExists(path(assetId));
        } catch (IOException failure) {
            throw new BlobRepositoryException("删除蓝图 blob 失败: " + assetId, failure);
        }
    }

    /** 启动/GC 使用的有界物理目录扫描；超限只进入新资产只读态，不阻断精确 ID 恢复。 */
    public ScanResult scan() {
        return scan(MAX_SCAN_ENTRIES, MAX_SCAN_COMPRESSED_BYTES);
    }

    /**
     * 以已经成功解码并完整校验的启动 manifest 作为保护集，关闭新资产写入并开始孤儿维护。
     * 保护集在维护结束前不可改变；运行期 tombstone 只会让它成为安全超集。
     */
    public synchronized void beginReconciliation(Set<TaskAssetId> liveAssetIds) {
        Objects.requireNonNull(liveAssetIds, "liveAssetIds");
        if (maintenanceLiveAssetIds != null) throw new IllegalStateException("blob reconciliation 已在运行");
        for (TaskAssetId assetId : liveAssetIds) {
            activeRootLeases.merge(assetId, 1, Integer::sum);
        }
        maintenanceLiveAssetIds = Set.copyOf(liveAssetIds);
    }

    /**
     * root 已 durable 移除后释放它拥有的一代 lease；相同确定性 ID 的新 root 已接管时只减计数不删文件。
     */
    public boolean deleteAfterRootRemovalIfMatches(TaskAssetId assetId, String sha256) {
        Objects.requireNonNull(assetId, "assetId");
        synchronized (assetLock(assetId)) {
            Integer leases = activeRootLeases.get(assetId);
            if (leases == null || leases <= 0) {
                throw new BlobRepositoryException("root removal 缺少对应 active lease: " + assetId);
            }
            if (leases == 1) activeRootLeases.remove(assetId);
            else activeRootLeases.put(assetId, leases - 1);
            if (activeRootLeases.containsKey(assetId) || admissionPins.containsKey(assetId)) {
                return false;
            }
            return deleteIfMatchesLocked(assetId, sha256);
        }
    }

    /**
     * 后台维护线程执行的一次有界 sweep。这里只按权威保护集删除明确 orphan，不解压正文。
     * 未完成或任一异常都会保留维护门禁，等待下次启动继续。
     */
    public ReconcileResult reconcileOrphans() {
        Set<TaskAssetId> live = maintenanceLiveAssetIds;
        if (live == null) throw new IllegalStateException("必须先建立 reconciliation 保护集");
        if (!Files.exists(directory)) {
            return finishReconciliation(new ReconcileResult(0, 0, true, false));
        }
        int visited = 0;
        int deleted = 0;
        boolean complete = true;
        try (var files = Files.list(directory)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                if (visited >= MAX_RECONCILE_ENTRIES) {
                    complete = false;
                    break;
                }
                Path file = iterator.next();
                visited++;
                if (!Files.isRegularFile(file)) {
                    throw new IOException("blob 目录包含未知非文件项: " + file.getFileName());
                }
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    TaskAssetId temporaryAssetId = parseTemporaryAssetId(name);
                    synchronized (assetLock(temporaryAssetId)) {
                        if (Files.deleteIfExists(file)) deleted++;
                    }
                    continue;
                }
                if (!name.endsWith(".nbt")) {
                    throw new IOException("blob 目录包含未知文件: " + name);
                }
                TaskAssetId assetId = TaskAssetId.parse(name.substring(0, name.length() - 4));
                if (live.contains(assetId)) continue;
                synchronized (assetLock(assetId)) {
                    if (maintenanceLiveAssetIds == null || maintenanceLiveAssetIds.contains(assetId)) {
                        continue;
                    }
                    if (Files.deleteIfExists(path(assetId))) deleted++;
                }
            }
            return finishReconciliation(new ReconcileResult(visited, deleted, complete, false));
        } catch (IOException | RuntimeException failure) {
            writeAdmissionReadOnly = true;
            return new ReconcileResult(visited, deleted, false, true);
        }
    }

    private synchronized ReconcileResult finishReconciliation(ReconcileResult sweep) {
        if (!sweep.complete() || sweep.failed()) {
            writeAdmissionReadOnly = true;
            return sweep;
        }
        ScanResult rescan;
        try {
            rescan = scan();
        } catch (RuntimeException failure) {
            writeAdmissionReadOnly = true;
            return new ReconcileResult(sweep.visitedEntries(), sweep.deletedOrphans(), false, true);
        }
        if (!rescan.complete() || rescan.quotaExceeded()) {
            writeAdmissionReadOnly = true;
            return new ReconcileResult(sweep.visitedEntries(), sweep.deletedOrphans(), false, false);
        }
        maintenanceLiveAssetIds = null;
        writeAdmissionReadOnly = false;
        return sweep;
    }

    ScanResult scan(int maxEntries, long maxCompressedBytes) {
        if (maxEntries <= 0 || maxCompressedBytes <= 0L) {
            throw new IllegalArgumentException("scan 配额必须为正数");
        }
        if (!Files.exists(directory)) return new ScanResult(List.of(), 0L, 0, true, false);
        List<TaskAssetId> ids = new ArrayList<>();
        long totalBytes = 0L;
        int visited = 0;
        int removedTemps = 0;
        boolean complete = true;
        boolean quotaExceeded = false;
        try (var files = Files.list(directory)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                if (visited >= maxEntries) {
                    complete = false;
                    quotaExceeded = true;
                    break;
                }
                Path file = iterator.next();
                visited++;
                if (!Files.isRegularFile(file)) {
                    throw new BlobRepositoryException("blob 目录包含未知非文件项: " + file.getFileName());
                }
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    TaskAssetId temporaryAssetId = parseTemporaryAssetId(name);
                    synchronized (assetLock(temporaryAssetId)) {
                        if (Files.deleteIfExists(file)) {
                            removedTemps++;
                        } else {
                            // 写线程已在等待期间完成发布；本轮目录视图不再能声称是完整快照。
                            complete = false;
                        }
                    }
                    continue;
                }
                if (!name.endsWith(".nbt")) {
                    throw new BlobRepositoryException("blob 目录包含未知文件: " + name);
                }
                long fileBytes = Files.size(file);
                if (fileBytes > maxCompressedBytes - totalBytes) {
                    complete = false;
                    quotaExceeded = true;
                    break;
                }
                totalBytes += fileBytes;
                ids.add(TaskAssetId.parse(name.substring(0, name.length() - 4)));
            }
        } catch (IOException failure) {
            throw new BlobRepositoryException("扫描蓝图 blob 目录失败", failure);
        }
        ids.sort(Comparator.naturalOrder());
        if (quotaExceeded) {
            writeAdmissionReadOnly = true;
        } else if (complete) {
            writeAdmissionReadOnly = false;
        }
        return new ScanResult(ids, totalBytes, removedTemps, complete, quotaExceeded);
    }

    private static boolean moveNew(Path temporary, Path target, AtomicMover mover) throws IOException {
        try {
            mover.move(temporary, target);
            return true;
        } catch (FileAlreadyExistsException raced) {
            Files.deleteIfExists(temporary);
            return false;
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("文件系统不支持同目录 ATOMIC_MOVE，拒绝降级发布蓝图 blob", unsupported);
        }
    }

    private static void atomicMove(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeAndForceNew(Path temporary, BlueprintBlobRecord record) throws IOException {
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            OutputStream output = Channels.newOutputStream(channel);
            codec.writeCompressed(record, output);
            output.flush();
            channel.force(true);
        }
    }

    private static void forceFile(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        } catch (IOException failure) {
            throw new BlobRepositoryException("强制落盘蓝图 blob 失败: " + file, failure);
        }
    }

    private static void forceDirectoryBestEffort(Path directory) {
        forceDirectoryBestEffort(directory, AtomicBlueprintBlobRepository::forceDirectoryChannel);
    }

    static void forceDirectoryBestEffort(Path directory, DirectoryForcer forcer) {
        try {
            forcer.force(directory);
        } catch (AccessDeniedException unsupported) {
            // Windows 的纯 Java NIO 无法可靠 fsync 目录；文件本体已 force 且只用原子移动，这是可达的最强语义。
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw new BlobRepositoryException("强制落盘 blob 目录失败: " + directory, unsupported);
            }
        } catch (UnsupportedOperationException unsupported) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) throw unsupported;
        } catch (IOException failure) {
            throw new BlobRepositoryException("强制落盘 blob 目录失败: " + directory, failure);
        }
    }

    private static void forceDirectoryChannel(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private Path path(TaskAssetId assetId) {
        Path resolved = directory.resolve(assetId + ".nbt").normalize();
        if (!resolved.getParent().equals(directory)) throw new IllegalArgumentException("assetId 路径越界");
        return resolved;
    }

    private static Object assetLock(TaskAssetId assetId) {
        int spread = assetId.hashCode() ^ (assetId.hashCode() >>> 16);
        return ASSET_LOCKS[spread & (LOCK_STRIPES - 1)];
    }

    private static Object[] createLockStripes() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
    }

    private static BlueprintBlobRecord requireFound(LoadResult result) {
        if (result instanceof LoadResult.Found found) return found.record();
        if (result instanceof LoadResult.Failed failed) {
            throw new BlobRepositoryException("读取现有蓝图 blob 失败，拒绝覆盖", failed.cause());
        }
        throw new BlobRepositoryException("预期蓝图 blob 存在但文件缺失");
    }

    /** 只承认本仓库真实写出的 assetId.nbt.UUID.tmp；任何近似名称都按未知文件 fail-closed。 */
    private static TaskAssetId parseTemporaryAssetId(String name) {
        int marker = name.indexOf(".nbt.");
        if (marker <= 0 || !name.endsWith(".tmp")) {
            throw new BlobRepositoryException("发现未知临时 blob 文件: " + name);
        }
        String token = name.substring(marker + ".nbt.".length(), name.length() - ".tmp".length());
        try {
            UUID writerTicket = UUID.fromString(token);
            if (!writerTicket.toString().equals(token)) {
                throw new IllegalArgumentException("UUID 非规范格式");
            }
            return TaskAssetId.parse(name.substring(0, marker));
        } catch (IllegalArgumentException malformed) {
            throw new BlobRepositoryException("发现未知临时 blob 文件: " + name, malformed);
        }
    }

    private static LoadResult.Found requireFoundResult(LoadResult result) {
        if (result instanceof LoadResult.Found found) return found;
        requireFound(result);
        throw new AssertionError("requireFound 应当已经抛出异常");
    }

    private static void requireSame(BlueprintBlobRecord existing, BlueprintBlobRecord requested) {
        if (!existing.assetId().equals(requested.assetId())
                || !existing.taskId().equals(requested.taskId())
                || !existing.sha256().equals(requested.sha256())
                || existing.blockCount() != requested.blockCount()) {
            throw new BlobRepositoryException("相同 assetId 已存在不同蓝图内容，拒绝覆盖: " + requested.assetId());
        }
    }

    public enum WriteOutcome { WRITTEN, ALREADY_PRESENT }

    private enum ProofRelease { CONSUME, PROMOTE_TO_ROOT, REJECT }

    /** receipt 经当前仓库回读后的事实，供 root admission 同时校验任务规模。 */
    public record VerifiedDurableBlueprintBlob(TaskAssetMetadata metadata, int blockCount) {
        public VerifiedDurableBlueprintBlob {
            Objects.requireNonNull(metadata, "metadata");
            if (blockCount <= 0) throw new IllegalArgumentException("blockCount 必须为正数");
        }
    }

    /** 仓库实例签发的证明；构造器故意私有，调用方只能交还给签发仓库验证。 */
    public static final class DurableBlueprintBlobReceipt {
        private final Object issuer;
        private final TaskAssetMetadata metadata;
        private final WriteOutcome outcome;

        private DurableBlueprintBlobReceipt(
                Object issuer, TaskAssetMetadata metadata, WriteOutcome outcome) {
            this.issuer = issuer;
            this.metadata = metadata;
            this.outcome = outcome;
        }

        public WriteOutcome outcome() {
            return outcome;
        }
    }

    /** 仅由签发仓库在后台完成物理回读后构造；主线程不得自行拼装。 */
    public static final class DurableBlueprintAdmissionProof {
        private final Object issuer;
        private final VerifiedDurableBlueprintBlob verified;
        private final WriteOutcome outcome;
        private boolean active = true;

        private DurableBlueprintAdmissionProof(
                Object issuer, VerifiedDurableBlueprintBlob verified, WriteOutcome outcome) {
            this.issuer = issuer;
            this.verified = verified;
            this.outcome = outcome;
        }

        public WriteOutcome outcome() {
            return outcome;
        }
    }

    public sealed interface LoadResult permits LoadResult.Found, LoadResult.Missing, LoadResult.Failed {
        record Found(BlueprintBlobRecord record, long compressedBytes) implements LoadResult { }
        record Missing() implements LoadResult { }
        record Failed(Throwable cause) implements LoadResult {
            public Failed { Objects.requireNonNull(cause, "cause"); }
        }
    }

    public record ScanResult(
            List<TaskAssetId> assetIds,
            long compressedBytes,
            int removedTemporaryFiles,
            boolean complete,
            boolean quotaExceeded) {
        public ScanResult { assetIds = List.copyOf(assetIds); }
    }

    public record ReconcileResult(
            int visitedEntries, int deletedOrphans, boolean complete, boolean failed) {
    }

    @FunctionalInterface
    interface AtomicMover {
        void move(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface DirectoryForcer {
        void force(Path directory) throws IOException;
    }

    public static final class BlobRepositoryException extends IllegalStateException {
        public BlobRepositoryException(String message) { super(message); }
        public BlobRepositoryException(String message, Throwable cause) { super(message, cause); }
    }
}
