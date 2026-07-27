package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 客户端只提交筛选意图；页面内容、链接储存和数量均由服务端构建。 */
public final class C2SRtsRequestStoragePagePayload implements IMessage {
    public static final int MAX_LOCALIZED_SEARCH_MATCHES = 256;
    public static final int MAX_PAGE_SIZE = 180;
    private int page, pageSize;
    private String search = "", category = "";
    private byte sort;
    private boolean ascending, pinyinSearchEnabled;
    private List<String> localizedSearchMatches = Collections.emptyList();

    public C2SRtsRequestStoragePagePayload() {
    }

    public C2SRtsRequestStoragePagePayload(int page, String search, String category, byte sort,
            boolean ascending, int pageSize, boolean pinyinSearchEnabled, List<String> matches) {
        this.page = page; this.search = search == null ? "" : search;
        this.category = category == null ? "" : category; this.sort = sort;
        this.ascending = ascending; this.pageSize = pageSize;
        this.pinyinSearchEnabled = pinyinSearchEnabled;
        this.localizedSearchMatches = immutableStrings(matches);
    }

    @Override public void fromBytes(ByteBuf b) {
        page = b.readInt();
        search = RtsPacketBuffer.readString(b, 128, "storage search");
        category = RtsPacketBuffer.readString(b, 128, "storage category");
        sort = b.readByte(); ascending = b.readBoolean(); pageSize = b.readInt();
        pinyinSearchEnabled = b.readBoolean();
        int size = RtsPacketBuffer.readBoundedCount(
                b, MAX_LOCALIZED_SEARCH_MATCHES, "localized search matches");
        List<String> matches = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            matches.add(RtsPacketBuffer.readString(b, 128, "localized item id"));
        }
        localizedSearchMatches = Collections.unmodifiableList(matches);
        if (!isValid()) throw new IllegalArgumentException("invalid storage page request");
    }

    @Override public void toBytes(ByteBuf b) {
        if (!isValid()) throw new IllegalArgumentException("invalid storage page request");
        b.writeInt(page);
        RtsPacketBuffer.writeString(b, search, 128, "storage search");
        RtsPacketBuffer.writeString(b, category, 128, "storage category");
        b.writeByte(sort); b.writeBoolean(ascending); b.writeInt(pageSize);
        b.writeBoolean(pinyinSearchEnabled);
        RtsPacketBuffer.writeVarInt(b, localizedSearchMatches.size());
        for (String match : localizedSearchMatches) {
            RtsPacketBuffer.writeString(b, match, 128, "localized item id");
        }
    }

    public boolean isValid() {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE || sort < 0
                || sort >= RtsStorageSort.values().length || search == null || search.length() > 128
                || category == null || category.length() > 128 || localizedSearchMatches == null
                || localizedSearchMatches.size() > MAX_LOCALIZED_SEARCH_MATCHES) return false;
        for (String match : localizedSearchMatches) if (match == null || match.length() > 128) return false;
        return true;
    }

    private static List<String> immutableStrings(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    public int page(){return page;} public String search(){return search;}
    public String category(){return category;} public byte sort(){return sort;}
    public boolean ascending(){return ascending;} public int pageSize(){return pageSize;}
    public boolean pinyinSearchEnabled(){return pinyinSearchEnabled;}
    public List<String> localizedSearchMatches(){return localizedSearchMatches;}
}
