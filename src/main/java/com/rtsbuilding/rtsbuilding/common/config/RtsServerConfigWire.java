package com.rtsbuilding.rtsbuilding.common.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置专用网络包的 loader 无关编码。
 *
 * <p>包只包含白名单字段和值，不接受文件路径、TOML 或任意对象。两条 loader
 * 只负责把这组受预算约束的字节交给自己的传输层，因此查询、修改和结果的字段
 * 顺序在 NeoForge/Forge 之间保持一致。</p>
 */
public final class RtsServerConfigWire {
    public static final int VERSION = 1;
    public static final int MAX_PACKET_BYTES = 32 * 1024;
    public static final int MAX_CHANGES = 64;
    public static final int MAX_MESSAGE_BYTES = 2048;
    private static final int QUERY = 1;
    private static final int UPDATE = 2;
    private static final int RESPONSE = 3;

    private RtsServerConfigWire() {
    }

    public record Query(long sessionId, int requestId) {
        public Query {
            requireRequestId(requestId);
        }
    }

    public record Update(long sessionId, int requestId, RtsServerConfigUpdateRequest request) {
        public Update {
            requireRequestId(requestId);
            if (request == null) {
                throw new IllegalArgumentException("missing configuration update");
            }
        }
    }

    public record Request(Query query, Update update) {
        public Request {
            if ((query == null) == (update == null)) {
                throw new IllegalArgumentException("configuration packet must contain one request");
            }
        }

        public boolean isQuery() {
            return query != null;
        }
    }

    public record Response(long sessionId, int requestId, RtsServerConfigUpdateResult result) {
        public Response {
            requireRequestId(requestId);
            if (result == null) {
                throw new IllegalArgumentException("missing configuration result");
            }
        }
    }

    public static byte[] encodeQuery(long sessionId, int requestId) {
        return encode(output -> {
            output.writeByte(VERSION);
            output.writeByte(QUERY);
            output.writeLong(sessionId);
            output.writeInt(requestId);
        });
    }

    public static byte[] encodeUpdate(long sessionId, int requestId,
                                      RtsServerConfigUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("missing configuration update");
        }
        return encode(output -> {
            output.writeByte(VERSION);
            output.writeByte(UPDATE);
            output.writeLong(sessionId);
            output.writeInt(requestId);
            output.writeInt(request.expectedRevision());
            output.writeShort(request.changes().size());
            for (RtsServerConfigChange change : request.changes()) {
                writeChange(output, change);
            }
        });
    }

    public static Request decodeRequest(byte[] encoded) {
        DataInputStream input = input(encoded);
        try {
            requireVersion(input.readUnsignedByte());
            int kind = input.readUnsignedByte();
            long session = input.readLong();
            int requestId = input.readInt();
            Request result;
            if (kind == QUERY) {
                result = new Request(new Query(session, requestId), null);
            } else if (kind == UPDATE) {
                int expectedRevision = input.readInt();
                int count = input.readUnsignedShort();
                if (count > MAX_CHANGES) {
                    throw new IllegalArgumentException("too many configuration changes");
                }
                List<RtsServerConfigChange> changes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    changes.add(readChange(input));
                }
                result = new Request(null, new Update(session, requestId,
                        new RtsServerConfigUpdateRequest(expectedRevision, changes)));
            } else {
                throw new IllegalArgumentException("unknown configuration request type: " + kind);
            }
            requireEnd(input);
            return result;
        } catch (EOFException failure) {
            throw new IllegalArgumentException("truncated configuration request", failure);
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid configuration request", failure);
        }
    }

    public static byte[] encodeResponse(long sessionId, int requestId,
                                        RtsServerConfigUpdateResult result) {
        if (result == null) {
            throw new IllegalArgumentException("missing configuration result");
        }
        return encode(output -> {
            output.writeByte(VERSION);
            output.writeByte(RESPONSE);
            output.writeLong(sessionId);
            output.writeInt(requestId);
            output.writeByte(result.status().ordinal());
            writeView(output, result.view());
            writeString(output, result.message(), MAX_MESSAGE_BYTES);
        });
    }

    public static Response decodeResponse(byte[] encoded) {
        DataInputStream input = input(encoded);
        try {
            requireVersion(input.readUnsignedByte());
            if (input.readUnsignedByte() != RESPONSE) {
                throw new IllegalArgumentException("not a configuration response");
            }
            long session = input.readLong();
            int requestId = input.readInt();
            int statusOrdinal = input.readUnsignedByte();
            RtsServerConfigUpdateResult.Status[] statuses = RtsServerConfigUpdateResult.Status.values();
            if (statusOrdinal >= statuses.length) {
                throw new IllegalArgumentException("unknown configuration result status: " + statusOrdinal);
            }
            RtsServerConfigView view = readView(input);
            String message = readString(input, MAX_MESSAGE_BYTES);
            requireEnd(input);
            return new Response(session, requestId,
                    new RtsServerConfigUpdateResult(statuses[statusOrdinal], view, message));
        } catch (EOFException failure) {
            throw new IllegalArgumentException("truncated configuration response", failure);
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid configuration response", failure);
        }
    }

    private static void writeChange(DataOutputStream output, RtsServerConfigChange change) throws IOException {
        if (change == null || change.key() == null || change.value() == null) {
            throw new IllegalArgumentException("null configuration change");
        }
        output.writeShort(change.key().ordinal());
        output.writeByte(change.value().type().ordinal());
        if (change.value() instanceof RtsServerConfigChange.BooleanValue value) {
            output.writeBoolean(value.value());
        } else if (change.value() instanceof RtsServerConfigChange.IntValue value) {
            output.writeInt(value.value());
        } else if (change.value() instanceof RtsServerConfigChange.LongValue value) {
            output.writeLong(value.value());
        } else if (change.value() instanceof RtsServerConfigChange.DoubleValue value) {
            output.writeDouble(value.value());
        } else if (change.value() instanceof RtsServerConfigChange.StringValue value) {
            writeString(output, value.value(), MAX_MESSAGE_BYTES);
        } else {
            throw new IllegalArgumentException("unsupported configuration value type");
        }
    }

    private static RtsServerConfigChange readChange(DataInputStream input) throws IOException {
        int keyOrdinal = input.readUnsignedShort();
        RtsServerConfigChange.Key[] keys = RtsServerConfigChange.Key.values();
        if (keyOrdinal >= keys.length) {
            throw new IllegalArgumentException("unknown configuration key: " + keyOrdinal);
        }
        int typeOrdinal = input.readUnsignedByte();
        RtsServerConfigChange.Type[] types = RtsServerConfigChange.Type.values();
        if (typeOrdinal >= types.length) {
            throw new IllegalArgumentException("unknown configuration value type: " + typeOrdinal);
        }
        RtsServerConfigChange.Key key = keys[keyOrdinal];
        RtsServerConfigChange.Type type = types[typeOrdinal];
        if (key.valueType() != type) {
            throw new IllegalArgumentException("configuration key/value type mismatch");
        }
        RtsServerConfigChange.Value value = switch (type) {
            case BOOLEAN -> new RtsServerConfigChange.BooleanValue(input.readBoolean());
            case INT -> new RtsServerConfigChange.IntValue(input.readInt());
            case LONG -> new RtsServerConfigChange.LongValue(input.readLong());
            case DOUBLE -> new RtsServerConfigChange.DoubleValue(input.readDouble());
            case STRING -> new RtsServerConfigChange.StringValue(readString(input, MAX_MESSAGE_BYTES));
        };
        return new RtsServerConfigChange(key, value);
    }

    private static void writeView(DataOutputStream output, RtsServerConfigView view) throws IOException {
        if (view == null) {
            throw new IllegalArgumentException("missing configuration view");
        }
        output.writeInt(view.revision());
        output.writeBoolean(view.editable());
        output.writeBoolean(view.worldLoaded());
        output.writeBoolean(view.enableSurvivalProgression());
        output.writeBoolean(view.shareSurvivalProgressionWithTeams());
        output.writeInt(view.maxActionRadiusBlocks());
        output.writeBoolean(view.enableBlueprints());
        output.writeInt(view.maxBlueprintBlocks());
        output.writeInt(view.maxSelectionVolume());
        output.writeInt(view.maxSelectionSizeX());
        output.writeInt(view.maxSelectionSizeY());
        output.writeInt(view.maxSelectionSizeZ());
        output.writeInt(view.ultimineMaxBlocks());
        output.writeInt(view.ultimineBlocksPerTick());
        writeString(output, view.areaMineMaxHarvestTier(), 32);
        output.writeInt(view.maxTreeBlocks());
        output.writeInt(view.homeSelectionRadiusBlocks());
        output.writeInt(view.homeRelocationCooldownDays());
        output.writeInt(view.maxShapeDimension());
        output.writeInt(view.maxShapeRadius());
        output.writeInt(view.smartFillMaxBlocks());
        output.writeInt(view.smartFillDefaultBlocks());
        output.writeInt(view.smartFillMaxDiameter());
        output.writeInt(view.smartFillDefaultDiameter());
        output.writeInt(view.maxBatchBindingSelectionVolume());
        output.writeInt(view.maxBatchBindingSizeX());
        output.writeInt(view.maxBatchBindingSizeY());
        output.writeInt(view.maxBatchBindingSizeZ());
        output.writeInt(view.maxLinkedStorages());
        output.writeDouble(view.funnelPickupRadiusBlocks());
        output.writeInt(view.workflowsMaxActivePerPlayer());
        output.writeInt(view.historyMaxEntriesPerStack());
        output.writeInt(view.historyRetentionSeconds());
        output.writeInt(view.funnelMaxEntitiesPerTick());
        output.writeInt(view.funnelMaxItemsPerTick());
        output.writeInt(view.funnelBufferMaxStacks());
        output.writeInt(view.funnelTickInterval());
        output.writeInt(view.storageDropCacheSoftCapacity());
        output.writeInt(view.buildBatchBlocksPerTick());
        output.writeInt(view.buildBatchMaxQueuedJobs());
        output.writeInt(view.taskEngineMaxUnitsPerTick());
        output.writeInt(view.taskEngineMaxUnitsPerSlice());
        output.writeLong(view.taskEngineMaxNanosPerTick());
        output.writeInt(view.defaultStoragePageSize());
        output.writeInt(view.maxStoragePageSize());
        output.writeInt(view.pageCacheMaxPlayers());
        output.writeInt(view.ae2NetworkRefreshThrottle());
        output.writeInt(view.refinedStorageNetworkRefreshThrottle());
        output.writeInt(view.diagnosticsMaxTraces());
        output.writeInt(view.diagnosticsMaxWorkflowLinks());
        output.writeInt(view.diagnosticsMaxTaskLinks());
    }

    private static RtsServerConfigView readView(DataInputStream input) throws IOException {
        int revision = input.readInt();
        boolean editable = input.readBoolean();
        boolean worldLoaded = input.readBoolean();
        boolean survival = input.readBoolean();
        boolean share = input.readBoolean();
        int actionRadius = input.readInt();
        boolean blueprints = input.readBoolean();
        int blueprintBlocks = input.readInt();
        int selectionVolume = input.readInt();
        int selectionX = input.readInt();
        int selectionY = input.readInt();
        int selectionZ = input.readInt();
        int chain = input.readInt();
        int chainTick = input.readInt();
        String harvest = readString(input, 32);
        int tree = input.readInt();
        int homeRadius = input.readInt();
        int cooldown = input.readInt();
        int shapeDimension = input.readInt();
        int shapeRadius = input.readInt();
        int smartMax = input.readInt();
        int smartDefault = input.readInt();
        int diameterMax = input.readInt();
        int diameterDefault = input.readInt();
        int batchVolume = input.readInt();
        int batchX = input.readInt();
        int batchY = input.readInt();
        int batchZ = input.readInt();
        int linked = input.readInt();
        double funnelRadius = input.readDouble();
        int workflows = input.readInt();
        int historyEntries = input.readInt();
        int historySeconds = input.readInt();
        int entities = input.readInt();
        int items = input.readInt();
        int buffer = input.readInt();
        int interval = input.readInt();
        int drop = input.readInt();
        int buildTick = input.readInt();
        int buildJobs = input.readInt();
        int unitsTick = input.readInt();
        int unitsSlice = input.readInt();
        long nanos = input.readLong();
        int page = input.readInt();
        int maxPage = input.readInt();
        int pagePlayers = input.readInt();
        int ae2 = input.readInt();
        int refined = input.readInt();
        int traces = input.readInt();
        int workflowLinks = input.readInt();
        int taskLinks = input.readInt();
        return new RtsServerConfigView(revision, editable, worldLoaded, survival, share, actionRadius, blueprints,
                blueprintBlocks, selectionVolume, selectionX, selectionY, selectionZ, chain, chainTick, harvest, tree,
                homeRadius, cooldown, shapeDimension, shapeRadius, smartMax, smartDefault, diameterMax, diameterDefault,
                batchVolume, batchX, batchY, batchZ, linked, funnelRadius, workflows, historyEntries, historySeconds,
                entities, items, buffer, interval, drop, buildTick, buildJobs, unitsTick, unitsSlice, nanos, page,
                maxPage, pagePlayers, ae2, refined, traces, workflowLinks, taskLinks);
    }

    private static byte[] encode(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length == 0 || result.length > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("configuration packet exceeds byte budget");
            }
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("could not encode configuration packet", failure);
        }
    }

    private static DataInputStream input(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("configuration packet exceeds byte budget");
        }
        return new DataInputStream(new ByteArrayInputStream(encoded));
    }

    private static void writeString(DataOutputStream output, String value, int maxBytes) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("configuration string exceeds byte budget");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maxBytes) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maxBytes) {
            throw new IllegalArgumentException("configuration string exceeds byte budget");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("truncated configuration string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireVersion(int version) {
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported configuration packet version: " + version);
        }
    }

    private static void requireRequestId(int requestId) {
        if (requestId < 0) {
            throw new IllegalArgumentException("configuration request id must be non-negative");
        }
    }

    private static void requireEnd(DataInputStream input) throws IOException {
        if (input.available() != 0) {
            throw new IllegalArgumentException("trailing configuration packet fields");
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}
