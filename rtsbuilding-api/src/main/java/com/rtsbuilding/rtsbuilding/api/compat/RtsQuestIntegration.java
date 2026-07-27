package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.server.level.ServerPlayer;
import javax.annotation.Nullable;

public interface RtsQuestIntegration {

    String getModId();

    boolean isAvailable();

    QuestDetectResult detect(ServerPlayer player);

    @Nullable
    String progressionTeamKey(ServerPlayer player);
}
