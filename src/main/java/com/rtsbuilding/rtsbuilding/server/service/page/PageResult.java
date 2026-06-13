package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

/**
 * Result of building a storage browser page.
 *
 * @param payload the page payload to send to the client
 * @param safePage the clamped page index actually used
 */
public record PageResult(S2CRtsStoragePagePayload payload, int safePage) {
}
