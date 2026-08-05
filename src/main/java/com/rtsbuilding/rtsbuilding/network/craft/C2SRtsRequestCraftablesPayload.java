package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class C2SRtsRequestCraftablesPayload implements IMessage {
    private static final int MAX_SEARCH_CHARS = 128;
    private static final int MAX_SEARCH_MATCHES = 8192;
    private static final int MAX_OFFSET = 1_000_000;
    private static final int MAX_LIMIT = 512;

    private String search = "";
    private boolean showUnavailable;
    private int offset;
    private int limit = 1;
    private boolean pinyinSearchEnabled;
    private List<String> localizedSearchMatches = Collections.emptyList();

    public C2SRtsRequestCraftablesPayload() {
    }

    public C2SRtsRequestCraftablesPayload(String search, boolean showUnavailable,
                                          int offset, int limit, boolean pinyinSearchEnabled,
                                          List<String> localizedSearchMatches) {
        this.search = search == null ? "" : search;
        this.showUnavailable = showUnavailable;
        this.offset = offset;
        this.limit = limit;
        this.pinyinSearchEnabled = pinyinSearchEnabled;
        this.localizedSearchMatches = immutable(localizedSearchMatches);
    }

    public String search() { return search; }
    public boolean showUnavailable() { return showUnavailable; }
    public int offset() { return offset; }
    public int limit() { return limit; }
    public boolean pinyinSearchEnabled() { return pinyinSearchEnabled; }
    public List<String> localizedSearchMatches() { return localizedSearchMatches; }

    public boolean isValid() {
        if (search.length() > MAX_SEARCH_CHARS || offset < 0 || offset > MAX_OFFSET
                || limit < 1 || limit > MAX_LIMIT
                || localizedSearchMatches.size() > MAX_SEARCH_MATCHES) return false;
        for (String value : localizedSearchMatches) {
            if (value == null || value.length() > MAX_SEARCH_CHARS) return false;
        }
        return true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        search = RtsPacketBuffer.readString(buffer, MAX_SEARCH_CHARS, "craft search");
        showUnavailable = buffer.readBoolean();
        offset = RtsPacketBuffer.readBoundedCount(buffer, MAX_OFFSET, "craft search offset");
        limit = RtsPacketBuffer.readBoundedCount(buffer, MAX_LIMIT, "craft search limit");
        pinyinSearchEnabled = buffer.readBoolean();
        int size = RtsPacketBuffer.readBoundedCount(buffer, MAX_SEARCH_MATCHES,
                "localized search match count");
        List<String> decoded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            decoded.add(RtsPacketBuffer.readString(buffer, MAX_SEARCH_CHARS,
                    "localized search match"));
        }
        localizedSearchMatches = immutable(decoded);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("craftables request is invalid");
        RtsPacketBuffer.writeString(buffer, search, MAX_SEARCH_CHARS, "craft search");
        buffer.writeBoolean(showUnavailable);
        RtsPacketBuffer.writeVarInt(buffer, offset);
        RtsPacketBuffer.writeVarInt(buffer, limit);
        buffer.writeBoolean(pinyinSearchEnabled);
        RtsPacketBuffer.writeVarInt(buffer, localizedSearchMatches.size());
        for (String value : localizedSearchMatches) {
            RtsPacketBuffer.writeString(buffer, value, MAX_SEARCH_CHARS,
                    "localized search match");
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
