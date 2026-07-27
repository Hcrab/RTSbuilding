package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

public final class ServiceOperationTemplate {

    private final RtsServer server;

    public ServiceOperationTemplate(RtsServer server) {
        this.server = server;
    }

    public void afterModification(ServerPlayer player, RtsStorageSession session) {
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        server.page().requestPage(player, session.browser.page, session.browser.search,
                session.browser.category, session.browser.sort, session.browser.ascending);
        server.session().saveToPlayerNbt(player, session);
    }

    public void simpleSave(ServerPlayer player, RtsStorageSession session) {
        server.page().requestPage(player, session.browser.page, session.browser.search,
                session.browser.category, session.browser.sort, session.browser.ascending);
        server.session().saveToPlayerNbt(player, session);
    }

    public void markDirty(ServerPlayer player, RtsStorageSession session) {
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
    }

    public void refreshPage(ServerPlayer player, RtsStorageSession session) {
        server.page().requestPage(player, session.browser.page, session.browser.search,
                session.browser.category, session.browser.sort, session.browser.ascending);
    }
}
