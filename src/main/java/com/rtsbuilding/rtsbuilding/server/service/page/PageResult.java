package com.rtsbuilding.rtsbuilding.server.service.page;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
public final class PageResult {
 private final S2CRtsStoragePagePayload payload; private final int safePage;
 public PageResult(S2CRtsStoragePagePayload payload,int safePage){this.payload=payload;this.safePage=safePage;}
 public S2CRtsStoragePagePayload payload(){return payload;} public int safePage(){return safePage;}
}
