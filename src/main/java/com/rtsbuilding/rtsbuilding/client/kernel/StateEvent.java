package com.rtsbuilding.rtsbuilding.client.kernel;

import com.rtsbuilding.rtsbuilding.client.domain.state.CraftFeedbackInfo;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.core.BlockPos;


public sealed interface StateEvent {

    
    record RtsToggled(boolean enabled) implements StateEvent {}

    
    record AnchorUpdated(double x, double y, double z, double maxRadius) implements StateEvent {}

    
    record BuilderModeChanged(BuilderMode mode) implements StateEvent {}

    
    record StoragePageLoaded(int revision, S2CRtsStoragePagePayload payload) implements StateEvent {}

    
    record CraftFeedbackReceived(CraftFeedbackInfo info) implements StateEvent {}

    
    record DamageTaken(float amount, boolean lowHealth, float health) implements StateEvent {}

    
    record WorkflowProgressed(int slot) implements StateEvent {}

    
    record PlayerDied() implements StateEvent {}

    
    record BlueprintCaptureComplete(BlockPos min, BlockPos max) implements StateEvent {}

    
    record ItemSelected(String itemId, String label) implements StateEvent {}

    
    record RemoteMenuOpened() implements StateEvent {}

    
    record RemoteMenuClosed() implements StateEvent {}

    
    record Custom(String type, Object data) implements StateEvent {}
}
