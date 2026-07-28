package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetManifest;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraft.nbt.NBTBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskCodecTest {
    private final TaskCodec codec = new TaskCodec();

    @Test
    void fullImageRoundTripPreservesTaskPayloadTombstoneAndMigrationLedger() {
        TaskSnapshot task = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 31,
                TaskLifecycleState.WAITING_RESOURCE,
                new TaskWaitKey("item", "minecraft:stone"), 4L, "minecraft:overworld");
        TaskTombstone tombstone = new TaskTombstone(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                7L, TaskLifecycleState.COMPLETED, 200L, 1_000L);
        TaskRepository.Image source = new TaskRepository.Image(
                Map.of(task.id(), task), Map.of(tombstone.taskId(), tombstone),
                Set.of("session-jobs-v1"), TaskAssetManifest.empty());

        TaskRepository.Image decoded = codec.decodeImage(codec.encodeImage(source));

        assertEquals(source, decoded);
    }

    @Test
    void schemaV2RoundTripPreservesMandatoryAssetManifest() {
        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        TaskSnapshot task = blueprintTask(taskId, assetId);
        TaskAssetMetadata metadata = metadata(taskId);
        TaskRepository.Image source = new TaskRepository.Image(
                Map.of(taskId, task), Map.of(), Set.of(),
                new TaskAssetManifest(Map.of(assetId, metadata)));

        NBTTagCompound encoded = codec.encodeImage(source);
        TaskRepository.Image decoded = codec.decodeImage(encoded);

        assertEquals(TaskCodec.CURRENT_SCHEMA, encoded.getInteger("schema"));
        assertTrue(NbtCompat.hasType(encoded, "assets", Constants.NBT.TAG_LIST));
        assertEquals(source, decoded);
    }

    @Test
    void schemaV1ExplicitlyLoadsWithEmptyManifestButV2RequiresAssets() {
        NBTTagCompound legacy = codec.encodeImage(TaskRepository.Image.empty());
        legacy.setInteger("schema", TaskCodec.LEGACY_SCHEMA);
        legacy.removeTag("assets");

        TaskRepository.Image decodedLegacy = codec.decodeImage(legacy);

        assertTrue(decodedLegacy.assets().entries().isEmpty());
        assertEquals(TaskCodec.CURRENT_SCHEMA, codec.encodeImage(decodedLegacy).getInteger("schema"));

        NBTTagCompound missingV2Assets = codec.encodeImage(TaskRepository.Image.empty());
        missingV2Assets.removeTag("assets");
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(missingV2Assets));
    }

    @Test
    void unknownSchemaUnknownRootFieldAndCrossRecordAssetMismatchFailClosed() {
        NBTTagCompound unknownSchema = codec.encodeImage(TaskRepository.Image.empty());
        unknownSchema.setInteger("schema", TaskCodec.CURRENT_SCHEMA + 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(unknownSchema));

        NBTTagCompound unknownField = codec.encodeImage(TaskRepository.Image.empty());
        unknownField.setInteger("queue_sequence", 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(unknownField));

        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        TaskRepository.Image valid = new TaskRepository.Image(
                Map.of(taskId, blueprintTask(taskId, assetId)), Map.of(), Set.of(),
                new TaskAssetManifest(Map.of(assetId, metadata(taskId))));
        NBTTagCompound mismatched = codec.encodeImage(valid);
        mismatched.getTagList("tasks", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0)
                .getCompoundTag("payload").setUniqueId("asset_id", UUID.randomUUID());

        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(mismatched));

        NBTTagCompound uppercaseSha = codec.encodeImage(valid);
        NBTTagCompound asset = uppercaseSha.getTagList("assets", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0);
        asset.setString("sha256", asset.getString("sha256").toUpperCase(java.util.Locale.ROOT));
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(uppercaseSha));
    }

    @Test
    void payloadIsDeepCopiedAtConstructionAndReadBoundary() {
        NBTTagCompound sourcePayload = new NBTTagCompound();
        sourcePayload.setInteger("cursor_blob", 4);
        TaskSnapshot task = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.MINING,
                TaskLifecycleState.RUNNING, -1, null, 1L, 0L, 0L,
                10, 0, 0, 0, sourcePayload);

        sourcePayload.setInteger("cursor_blob", 99);
        NBTTagCompound exposed = task.payload();
        exposed.setInteger("cursor_blob", 77);

        assertEquals(4, task.payload().getInteger("cursor_blob"));
    }

    @Test
    void unknownOrCorruptSchemaCannotBecomeEmptyRepository() {
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setInteger("schema", TaskCodec.CURRENT_SCHEMA + 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(unknown));

        NBTTagCompound corrupt = new NBTTagCompound();
        corrupt.setInteger("schema", TaskCodec.CURRENT_SCHEMA);
        net.minecraft.nbt.NBTTagList tasks = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound missingIdentity = new NBTTagCompound();
        missingIdentity.setString("type", "PLACEMENT");
        tasks.appendTag(missingIdentity);
        corrupt.setTag("tasks", tasks);
        corrupt.setTag("tombstones", new net.minecraft.nbt.NBTTagList());
        corrupt.setTag("completed_migrations", new net.minecraft.nbt.NBTTagList());
        corrupt.setTag("assets", new net.minecraft.nbt.NBTTagList());
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(corrupt));
    }

    @Test
    void rootListsAndPayloadRequireExactNbtTypes() {
        NBTTagCompound wrongRootList = new NBTTagCompound();
        wrongRootList.setInteger("schema", TaskCodec.CURRENT_SCHEMA);
        wrongRootList.setString("tasks", "not-a-list");
        wrongRootList.setTag("tombstones", new net.minecraft.nbt.NBTTagList());
        wrongRootList.setTag("completed_migrations", new net.minecraft.nbt.NBTTagList());
        wrongRootList.setTag("assets", new net.minecraft.nbt.NBTTagList());
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(wrongRootList));

        NBTTagCompound wrongElementType = new NBTTagCompound();
        wrongElementType.setInteger("schema", TaskCodec.CURRENT_SCHEMA);
        net.minecraft.nbt.NBTTagList stringTasks = new net.minecraft.nbt.NBTTagList();
        stringTasks.appendTag(new net.minecraft.nbt.NBTTagString("not-a-task"));
        wrongElementType.setTag("tasks", stringTasks);
        wrongElementType.setTag("tombstones", new net.minecraft.nbt.NBTTagList());
        wrongElementType.setTag("completed_migrations", new net.minecraft.nbt.NBTTagList());
        wrongElementType.setTag("assets", new net.minecraft.nbt.NBTTagList());
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(wrongElementType));

        TaskSnapshot task = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 55,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        NBTTagCompound encoded = codec.encodeSnapshot(task);
        encoded.setInteger("payload", 7);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeSnapshot(encoded));
    }

    @Test
    void modifiedUtfLimitRejectsStringsThatFitCharCountButNotNbtBytes() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("text", "界".repeat(22_000));
        TaskSnapshot task = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.PLACEMENT,
                TaskLifecycleState.QUEUED, -1, null, 1L, 0L, 0L,
                1, 0, 0, 0, payload);

        assertThrows(IllegalArgumentException.class, () -> codec.estimateSnapshotBytes(task));
    }

    @Test
    void migrationLedgerCapacityIsBounded() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("schema", TaskCodec.CURRENT_SCHEMA);
        root.setTag("tasks", new net.minecraft.nbt.NBTTagList());
        root.setTag("tombstones", new net.minecraft.nbt.NBTTagList());
        net.minecraft.nbt.NBTTagList migrations = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i <= TaskCodec.MAX_MIGRATIONS; i++) {
            migrations.appendTag(new net.minecraft.nbt.NBTTagString("migration-" + i));
        }
        root.setTag("completed_migrations", migrations);
        root.setTag("assets", new net.minecraft.nbt.NBTTagList());

        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(root));
    }

    @Test
    void optionalWorkflowDefaultsOnlyWhenAbsentAndRejectsWrongType() {
        TaskSnapshot task = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.PLACEMENT,
                TaskLifecycleState.QUEUED, -1, null, 1L, 0L, 0L,
                1, 0, 0, 0, new NBTTagCompound());
        NBTTagCompound absent = codec.encodeSnapshot(task);
        assertEquals(-1, codec.decodeSnapshot(absent).workflowEntryId());

        absent.setString("workflow", "wrong-type");
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeSnapshot(absent));
    }

    @Test
    void optionalWaitDefaultsOnlyWhenAbsentAndRejectsWrongType() {
        TaskSnapshot task = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.MINING,
                TaskLifecycleState.QUEUED, -1, null, 1L, 0L, 0L,
                1, 0, 0, 0, new NBTTagCompound());
        NBTTagCompound absent = codec.encodeSnapshot(task);
        assertNull(codec.decodeSnapshot(absent).waitKey());

        absent.setString("wait", "wrong-type");
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeSnapshot(absent));
    }

    @Test
    void snapshotWaitAndTombstoneRejectUnknownEnvelopeFields() {
        TaskSnapshot task = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 88,
                TaskLifecycleState.WAITING_RESOURCE,
                new TaskWaitKey("item", "minecraft:stone"), 1L, "minecraft:overworld");
        NBTTagCompound snapshot = codec.encodeSnapshot(task);
        snapshot.setInteger("future_field", 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeSnapshot(snapshot));

        NBTTagCompound wait = codec.encodeSnapshot(task);
        wait.getCompoundTag("wait").setInteger("future_field", 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeSnapshot(wait));

        TaskTombstone tombstone = new TaskTombstone(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                2L, TaskLifecycleState.COMPLETED, 20L, 40L);
        TaskRepository.Image image = new TaskRepository.Image(
                Map.of(), Map.of(tombstone.taskId(), tombstone), Set.of(), TaskAssetManifest.empty());
        NBTTagCompound root = codec.encodeImage(image);
        root.getTagList("tombstones", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("future_field", 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(root));
    }

    @Test
    void duplicateMigrationLedgerEntryFailsClosed() {
        NBTTagCompound root = codec.encodeImage(TaskRepository.Image.empty());
        net.minecraft.nbt.NBTTagList migrations = root.getTagList(
                "completed_migrations", Constants.NBT.TAG_STRING);
        migrations.appendTag(new net.minecraft.nbt.NBTTagString("same"));
        migrations.appendTag(new net.minecraft.nbt.NBTTagString("same"));

        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(root));
    }

    @Test
    void completeImageBudgetCountsEveryRootSectionSymmetrically() {
        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        TaskRepository.Image image = new TaskRepository.Image(
                Map.of(taskId, blueprintTask(taskId, assetId)), Map.of(), Set.of("migration-v2"),
                new TaskAssetManifest(Map.of(assetId, metadata(taskId))));
        long exact = codec.estimateImageBytes(image);

        codec.requireImageBudget(image, exact);
        assertThrows(TaskCodec.TaskCodecException.class,
                () -> codec.requireImageBudget(image, exact - 1L));
        assertEquals(image, codec.decodeImage(codec.encodeImage(image)));
    }

    @Test
    void dimensionMustBeCanonicalResourceLocationAndWaitKeyCountsTowardBudget() {
        assertThrows(IllegalArgumentException.class, () -> new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "Bad Dimension",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.MINING,
                TaskLifecycleState.QUEUED, -1, null, 1L, 0L, 0L,
                1, 0, 0, 0, new NBTTagCompound()));

        TaskSnapshot plain = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.MINING,
                TaskLifecycleState.QUEUED, -1, null, 1L, 0L, 0L,
                1, 0, 0, 0, new NBTTagCompound());
        TaskSnapshot waiting = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.MINING,
                TaskLifecycleState.WAITING_RESOURCE, -1,
                new TaskWaitKey("item", "minecraft:oak_log"), 1L, 0L, 0L,
                1, 0, 0, 0, new NBTTagCompound());
        assertTrue(codec.estimateSnapshotBytes(waiting) > codec.estimateSnapshotBytes(plain));
    }

    private static TaskAssetMetadata metadata(TaskId taskId) {
        return new TaskAssetMetadata(TaskAssetId.forTask(taskId, "blueprint"), taskId,
                "blueprint", "a".repeat(64), 512L, 4_096L);
    }

    private static TaskSnapshot blueprintTask(TaskId taskId, TaskAssetId assetId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setUniqueId("asset_id", assetId.value());
        return new TaskSnapshot(taskId, SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                TaskType.BLUEPRINT, TaskLifecycleState.QUEUED, -1, null,
                1L, 0L, 0L, 12, 0, 0, 0, payload);
    }
}
