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

/** loader 无关的配置专用白名单网络编码；不接受文件路径、TOML 或任意对象。 */
public final class RtsServerConfigWire {
    public static final int VERSION = 1;
    public static final int MAX_PACKET_BYTES = 32 * 1024;
    public static final int MAX_CHANGES = 64;
    public static final int MAX_MESSAGE_BYTES = 2048;
    private static final int QUERY = 1;
    private static final int UPDATE = 2;
    private static final int RESPONSE = 3;
    private RtsServerConfigWire() {}

    public record Query(long sessionId, int requestId) { public Query { requireRequestId(requestId); } }
    public record Update(long sessionId, int requestId, RtsServerConfigUpdateRequest request) {
        public Update { requireRequestId(requestId); if (request == null) throw new IllegalArgumentException("missing configuration update"); }
    }
    public record Request(Query query, Update update) {
        public Request { if ((query == null) == (update == null)) throw new IllegalArgumentException("configuration packet must contain one request"); }
        public boolean isQuery() { return query != null; }
    }
    public record Response(long sessionId, int requestId, RtsServerConfigUpdateResult result) {
        public Response { requireRequestId(requestId); if (result == null) throw new IllegalArgumentException("missing configuration result"); }
    }

    public static byte[] encodeQuery(long sessionId, int requestId) { return encode(o -> { o.writeByte(VERSION); o.writeByte(QUERY); o.writeLong(sessionId); o.writeInt(requestId); }); }
    public static byte[] encodeUpdate(long sessionId, int requestId, RtsServerConfigUpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("missing configuration update");
        return encode(o -> { o.writeByte(VERSION); o.writeByte(UPDATE); o.writeLong(sessionId); o.writeInt(requestId); o.writeInt(request.expectedRevision()); o.writeShort(request.changes().size()); for (RtsServerConfigChange change : request.changes()) writeChange(o, change); });
    }
    public static Request decodeRequest(byte[] encoded) {
        DataInputStream in = input(encoded);
        try {
            requireVersion(in.readUnsignedByte()); int kind = in.readUnsignedByte(); long session = in.readLong(); int requestId = in.readInt(); Request result;
            if (kind == QUERY) result = new Request(new Query(session, requestId), null);
            else if (kind == UPDATE) { int revision = in.readInt(); int count = in.readUnsignedShort(); if (count > MAX_CHANGES) throw new IllegalArgumentException("too many configuration changes"); List<RtsServerConfigChange> changes = new ArrayList<>(count); for (int i = 0; i < count; i++) changes.add(readChange(in)); result = new Request(null, new Update(session, requestId, new RtsServerConfigUpdateRequest(revision, changes))); }
            else throw new IllegalArgumentException("unknown configuration request type: " + kind);
            requireEnd(in); return result;
        } catch (EOFException failure) { throw new IllegalArgumentException("truncated configuration request", failure); }
        catch (IOException failure) { throw new IllegalArgumentException("invalid configuration request", failure); }
    }
    public static byte[] encodeResponse(long sessionId, int requestId, RtsServerConfigUpdateResult result) {
        if (result == null) throw new IllegalArgumentException("missing configuration result");
        return encode(o -> { o.writeByte(VERSION); o.writeByte(RESPONSE); o.writeLong(sessionId); o.writeInt(requestId); o.writeByte(result.status().ordinal()); writeView(o, result.view()); writeString(o, result.message(), MAX_MESSAGE_BYTES); });
    }
    public static Response decodeResponse(byte[] encoded) {
        DataInputStream in = input(encoded);
        try {
            requireVersion(in.readUnsignedByte()); if (in.readUnsignedByte() != RESPONSE) throw new IllegalArgumentException("not a configuration response"); long session = in.readLong(); int requestId = in.readInt(); int ordinal = in.readUnsignedByte(); RtsServerConfigUpdateResult.Status[] statuses = RtsServerConfigUpdateResult.Status.values(); if (ordinal >= statuses.length) throw new IllegalArgumentException("unknown configuration result status: " + ordinal); RtsServerConfigView view = readView(in); String message = readString(in, MAX_MESSAGE_BYTES); requireEnd(in); return new Response(session, requestId, new RtsServerConfigUpdateResult(statuses[ordinal], view, message));
        } catch (EOFException failure) { throw new IllegalArgumentException("truncated configuration response", failure); }
        catch (IOException failure) { throw new IllegalArgumentException("invalid configuration response", failure); }
    }
    private static void writeChange(DataOutputStream o, RtsServerConfigChange c) throws IOException {
        if (c == null || c.key() == null || c.value() == null) throw new IllegalArgumentException("null configuration change");
        o.writeShort(c.key().ordinal()); o.writeByte(c.value().type().ordinal());
        if (c.value() instanceof RtsServerConfigChange.BooleanValue value) {
            o.writeBoolean(value.value());
        } else if (c.value() instanceof RtsServerConfigChange.IntValue value) {
            o.writeInt(value.value());
        } else if (c.value() instanceof RtsServerConfigChange.LongValue value) {
            o.writeLong(value.value());
        } else if (c.value() instanceof RtsServerConfigChange.DoubleValue value) {
            o.writeDouble(value.value());
        } else if (c.value() instanceof RtsServerConfigChange.StringValue value) {
            writeString(o, value.value(), MAX_MESSAGE_BYTES);
        } else {
            throw new IllegalArgumentException("unsupported configuration value type");
        }
    }
    private static RtsServerConfigChange readChange(DataInputStream in) throws IOException {
        int keyOrdinal = in.readUnsignedShort(); RtsServerConfigChange.Key[] keys = RtsServerConfigChange.Key.values(); if (keyOrdinal >= keys.length) throw new IllegalArgumentException("unknown configuration key: " + keyOrdinal);
        int typeOrdinal = in.readUnsignedByte(); RtsServerConfigChange.Type[] types = RtsServerConfigChange.Type.values(); if (typeOrdinal >= types.length) throw new IllegalArgumentException("unknown configuration value type: " + typeOrdinal);
        RtsServerConfigChange.Key key = keys[keyOrdinal]; RtsServerConfigChange.Type type = types[typeOrdinal]; if (key.valueType() != type) throw new IllegalArgumentException("configuration key/value type mismatch");
        RtsServerConfigChange.Value value = switch (type) { case BOOLEAN -> new RtsServerConfigChange.BooleanValue(in.readBoolean()); case INT -> new RtsServerConfigChange.IntValue(in.readInt()); case LONG -> new RtsServerConfigChange.LongValue(in.readLong()); case DOUBLE -> new RtsServerConfigChange.DoubleValue(in.readDouble()); case STRING -> new RtsServerConfigChange.StringValue(readString(in, MAX_MESSAGE_BYTES)); };
        return new RtsServerConfigChange(key, value);
    }
    private static void writeView(DataOutputStream o, RtsServerConfigView v) throws IOException {
        if (v == null) throw new IllegalArgumentException("missing configuration view");
        o.writeInt(v.revision()); o.writeBoolean(v.editable()); o.writeBoolean(v.worldLoaded()); o.writeBoolean(v.enableSurvivalProgression()); o.writeBoolean(v.shareSurvivalProgressionWithTeams()); o.writeInt(v.maxActionRadiusBlocks()); o.writeBoolean(v.enableBlueprints()); o.writeInt(v.maxBlueprintBlocks()); o.writeInt(v.maxSelectionVolume()); o.writeInt(v.maxSelectionSizeX()); o.writeInt(v.maxSelectionSizeY()); o.writeInt(v.maxSelectionSizeZ()); o.writeInt(v.ultimineMaxBlocks()); o.writeInt(v.ultimineBlocksPerTick()); writeString(o, v.areaMineMaxHarvestTier(), 32); o.writeInt(v.maxTreeBlocks()); o.writeInt(v.homeSelectionRadiusBlocks()); o.writeInt(v.homeRelocationCooldownDays()); o.writeInt(v.maxShapeDimension()); o.writeInt(v.maxShapeRadius()); o.writeInt(v.smartFillMaxBlocks()); o.writeInt(v.smartFillDefaultBlocks()); o.writeInt(v.smartFillMaxDiameter()); o.writeInt(v.smartFillDefaultDiameter()); o.writeInt(v.maxBatchBindingSelectionVolume()); o.writeInt(v.maxBatchBindingSizeX()); o.writeInt(v.maxBatchBindingSizeY()); o.writeInt(v.maxBatchBindingSizeZ()); o.writeInt(v.maxLinkedStorages()); o.writeDouble(v.funnelPickupRadiusBlocks()); o.writeInt(v.workflowsMaxActivePerPlayer()); o.writeInt(v.historyMaxEntriesPerStack()); o.writeInt(v.historyRetentionSeconds()); o.writeInt(v.funnelMaxEntitiesPerTick()); o.writeInt(v.funnelMaxItemsPerTick()); o.writeInt(v.funnelBufferMaxStacks()); o.writeInt(v.funnelTickInterval()); o.writeInt(v.storageDropCacheSoftCapacity()); o.writeInt(v.buildBatchBlocksPerTick()); o.writeInt(v.buildBatchMaxQueuedJobs()); o.writeInt(v.taskEngineMaxUnitsPerTick()); o.writeInt(v.taskEngineMaxUnitsPerSlice()); o.writeLong(v.taskEngineMaxNanosPerTick()); o.writeInt(v.defaultStoragePageSize()); o.writeInt(v.maxStoragePageSize()); o.writeInt(v.pageCacheMaxPlayers()); o.writeInt(v.ae2NetworkRefreshThrottle()); o.writeInt(v.refinedStorageNetworkRefreshThrottle()); o.writeInt(v.diagnosticsMaxTraces()); o.writeInt(v.diagnosticsMaxWorkflowLinks()); o.writeInt(v.diagnosticsMaxTaskLinks());
    }
    private static RtsServerConfigView readView(DataInputStream in) throws IOException {
        int revision=in.readInt(); boolean editable=in.readBoolean(); boolean worldLoaded=in.readBoolean(); boolean survival=in.readBoolean(); boolean share=in.readBoolean(); int action=in.readInt(); boolean blueprints=in.readBoolean(); int blueprint=in.readInt(); int volume=in.readInt(); int x=in.readInt(); int y=in.readInt(); int z=in.readInt(); int chain=in.readInt(); int chainTick=in.readInt(); String harvest=readString(in,32); int tree=in.readInt(); int home=in.readInt(); int cooldown=in.readInt(); int shape=in.readInt(); int radius=in.readInt(); int smart=in.readInt(); int smartDefault=in.readInt(); int diameter=in.readInt(); int diameterDefault=in.readInt(); int batch=in.readInt(); int batchX=in.readInt(); int batchY=in.readInt(); int batchZ=in.readInt(); int linked=in.readInt(); double funnel=in.readDouble(); int workflows=in.readInt(); int history=in.readInt(); int historySeconds=in.readInt(); int entities=in.readInt(); int items=in.readInt(); int buffer=in.readInt(); int interval=in.readInt(); int drop=in.readInt(); int buildTick=in.readInt(); int buildJobs=in.readInt(); int units=in.readInt(); int slice=in.readInt(); long nanos=in.readLong(); int page=in.readInt(); int maxPage=in.readInt(); int players=in.readInt(); int ae2=in.readInt(); int refined=in.readInt(); int traces=in.readInt(); int workflowLinks=in.readInt(); int taskLinks=in.readInt();
        return new RtsServerConfigView(revision, editable, worldLoaded, survival, share, action, blueprints, blueprint, volume, x, y, z, chain, chainTick, harvest, tree, home, cooldown, shape, radius, smart, smartDefault, diameter, diameterDefault, batch, batchX, batchY, batchZ, linked, funnel, workflows, history, historySeconds, entities, items, buffer, interval, drop, buildTick, buildJobs, units, slice, nanos, page, maxPage, players, ae2, refined, traces, workflowLinks, taskLinks);
    }
    private static byte[] encode(Writer writer) { try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024); DataOutputStream out = new DataOutputStream(bytes); writer.write(out); out.flush(); byte[] result = bytes.toByteArray(); if (result.length == 0 || result.length > MAX_PACKET_BYTES) throw new IllegalArgumentException("configuration packet exceeds byte budget"); return result; } catch (IOException failure) { throw new IllegalArgumentException("could not encode configuration packet", failure); } }
    private static DataInputStream input(byte[] encoded) { if (encoded == null || encoded.length == 0 || encoded.length > MAX_PACKET_BYTES) throw new IllegalArgumentException("configuration packet exceeds byte budget"); return new DataInputStream(new ByteArrayInputStream(encoded)); }
    private static void writeString(DataOutputStream o, String value, int maxBytes) throws IOException { byte[] b = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8); if (b.length > maxBytes) throw new IllegalArgumentException("configuration string exceeds byte budget"); o.writeShort(b.length); o.write(b); }
    private static String readString(DataInputStream in, int maxBytes) throws IOException { int length = in.readUnsignedShort(); if (length > maxBytes) throw new IllegalArgumentException("configuration string exceeds byte budget"); byte[] b = in.readNBytes(length); if (b.length != length) throw new EOFException("truncated configuration string"); return new String(b, StandardCharsets.UTF_8); }
    private static void requireVersion(int version) { if (version != VERSION) throw new IllegalArgumentException("unsupported configuration packet version: " + version); }
    private static void requireRequestId(int id) { if (id < 0) throw new IllegalArgumentException("configuration request id must be non-negative"); }
    private static void requireEnd(DataInputStream in) throws IOException { if (in.available() != 0) throw new IllegalArgumentException("trailing configuration packet fields"); }
    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws IOException; }
}
