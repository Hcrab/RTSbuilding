package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.compat.RtsRemoteMenuClientDiagnostics;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.*;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsHomeScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.service.BuildPlacementService;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

final class ClientRtsLifecycleOwner {
    private final ClientRtsController controller;

    ClientRtsLifecycleOwner(ClientRtsController controller) {
        this.controller = controller;
    }

    void applyServerCameraState(S2CRtsCameraStatePayload payload) {
            Minecraft minecraft = Minecraft.getInstance();

            if (payload.enabled()) {
                boolean freshEnable = !controller.enabled;
                controller.enabled = true;
                controller.cameraOrbitService.setServerCameraEntityId(payload.cameraEntityId());
                controller.anchorX = payload.anchorX();
                controller.anchorY = payload.anchorY();
                controller.anchorZ = payload.anchorZ();
                controller.maxRadius = payload.maxRadius();
                controller.homeSelectionMode = payload.homeSelection();
                controller.closeRangeAllowed = payload.closeRangeAllowed();

                if (freshEnable) {
                    RtsRemoteMenuClientDiagnostics.reset("rts-enabled");
                    controller.cameraOrbitService.capturePreviousView(minecraft);
                    // Clear stale player input to prevent WASD presses from before entering RTS mode from affecting movement
                    if (minecraft.player instanceof LocalPlayer localPlayer) {
                        localPlayer.input.forwardImpulse = 0.0F;
                        localPlayer.input.leftImpulse = 0.0F;
                        localPlayer.input.jumping = false;
                        localPlayer.input.shiftKeyDown = false;
                    }
                }

                controller.cameraOrbitService.applyRtsView(minecraft);

                if (!(minecraft.screen instanceof BuilderScreen)) {
                    minecraft.setScreen(new BuilderScreen(controller));
                }

                controller.cameraOrbitService.applyEnabledPose(
                        payload.anchorX(), payload.anchorY(), payload.anchorZ(),
                        payload.heightOffset(), payload.yawDeg(), payload.pitchDeg());
                controller.storageStateManager.clearStorageState();
                controller.buildPlacementService.clearPlacementSelectionPreserveMode();
                controller.miningOperationService.clearMiningState();
                controller.lastFunnelTarget = null;
                controller.funnelTargetCooldownTicks = 0;
                controller.pendingCraftTerminalOpen = false;
                controller.pendingCraftTerminalOpenTicks = 0;
                controller.pendingRemoteMenuOpenTicks = 0;
                controller.screenlessRemoteMenuTicks = 0;
                controller.clearRemoteMenuValidationState();
                controller.storageStateManager.clearQuickSlotsLocal();
                controller.storageStateManager.clearGuiBindingsLocal();

                controller.cameraOrbitService.setBounds(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
                controller.cameraOrbitService.syncVisualCameraFrame(minecraft,
                        payload.anchorX(), payload.anchorY(), payload.anchorZ(),
                        payload.maxRadius(), true);
                controller.requestStoragePage(0);
                return;
            }

            controller.enabled = false;
            RtsRemoteMenuClientDiagnostics.reset("rts-disabled");
            controller.cameraOrbitService.resetServerCameraEntityId();
            controller.cameraOrbitService.setLocalStateReady(false);
            controller.homeSelectionMode = false;
            controller.closeRangeAllowed = false;
            controller.cameraOrbitService.clearState();
            controller.lastFunnelTarget = null;
            controller.funnelTargetCooldownTicks = 0;
            controller.pendingCraftTerminalOpen = false;
            controller.pendingCraftTerminalOpenTicks = 0;
            controller.pendingRemoteMenuOpenTicks = 0;
            controller.screenlessRemoteMenuTicks = 0;
            controller.clearRemoteMenuValidationState();

            controller.cameraOrbitService.endRotateCapture(0.0D, 0.0D);

            controller.buildPlacementService.clearPlacementSelectionPreserveMode();
            controller.miningOperationService.clearMiningRenderState();
            controller.storageStateManager.clearQuickSlotsLocal();
            controller.storageStateManager.clearGuiBindingsLocal();
            controller.storageStateManager.clearStorageScanState();
            controller.storageStateManager.clearStorageViewDirty();

            if (minecraft.screen instanceof BuilderScreen) {
                minecraft.setScreen(null);
            }

            controller.cameraOrbitService.restorePreviousView(minecraft, minecraft.player);
        }

    void applyServerCameraAnchor(S2CRtsCameraAnchorPayload payload) {
            if (!controller.enabled) {
                return;
            }
            controller.anchorX = payload.anchorX();
            controller.anchorY = payload.anchorY();
            controller.anchorZ = payload.anchorZ();
            controller.maxRadius = payload.maxRadius();
            controller.cameraOrbitService.setBounds(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
        }

    void preTick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || !controller.enabled) {
                controller.clearRemoteMenuValidationState();
                RtsRemoteMenuClientDiagnostics.reset("player-or-rts-unavailable");
            }
        }

    void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (!controller.enabled) {
                controller.suppressBuilderScreenRestoreUntilRtsRestart = false;
                return;
            }

            if (minecraft.player == null || minecraft.level == null) {
                return;
            }
            if (controller.handleDeathScreenHandoff(minecraft)) {
                return;
            }

            if (controller.funnelTargetCooldownTicks > 0) {
                controller.funnelTargetCooldownTicks--;
            }

            boolean hasRemoteMenuOpen = minecraft.player.containerMenu != null
                    && minecraft.player.containerMenu.containerId != 0;

            if (hasRemoteMenuOpen
                    && minecraft.screen == null
                    && controller.pendingRemoteMenuOpenTicks <= 0) {
                controller.screenlessRemoteMenuTicks++;
                if (controller.screenlessRemoteMenuTicks >= ClientRtsController.SCREENLESS_REMOTE_MENU_RECOVERY_TICKS) {
                    RtsRemoteMenuClientDiagnostics.screenlessRecovery(
                            minecraft.player.containerMenu,
                            controller.screenlessRemoteMenuTicks);
                    RtsClientPacketGateway.sendCloseRemoteMenu();
                    minecraft.player.closeContainer();
                    controller.clearRemoteMenuValidationState();
                    controller.relaxedRemoteMenu = null;
                    hasRemoteMenuOpen = false;
                    controller.screenlessRemoteMenuTicks = 0;
                }
            } else {
                controller.screenlessRemoteMenuTicks = 0;
            }

            if (minecraft.screen instanceof RtsCraftTerminalScreen) {
                controller.pendingCraftTerminalOpen = false;
                controller.pendingCraftTerminalOpenTicks = 0;
            } else if (controller.pendingCraftTerminalOpen) {
                if (controller.pendingCraftTerminalOpenTicks > 0) {
                    controller.pendingCraftTerminalOpenTicks--;
                } else {
                    controller.pendingCraftTerminalOpen = false;
                }
            }

            if (hasRemoteMenuOpen) {
                controller.pendingRemoteMenuOpenTicks = 0;
                try {
                    AbstractContainerMenu activeRemoteMenu = RtsClientRemoteMenuCompat.install(minecraft, minecraft.player.containerMenu);
                    if (controller.relaxedRemoteMenu != activeRemoteMenu) {
                        RtsClientRemoteMenuCompat.RelaxationReport relaxationReport =
                                RtsClientRemoteMenuCompat.relaxValidation(activeRemoteMenu);
                        RtsRemoteMenuClientDiagnostics.validationApplied(activeRemoteMenu, relaxationReport);
                        controller.relaxedRemoteMenu = activeRemoteMenu;
                    }
                    if (minecraft.screen instanceof BuilderScreen) {
                        // First-open GUI construction can leave a brief null-screen handoff. Once a real
                        // container menu exists, let it take over instead of keeping BuilderScreen active.
                        minecraft.setScreen(null);
                    }
                } catch (Throwable throwable) {
                    controller.handleRemoteMenuOpenFailure(minecraft, throwable);
                    hasRemoteMenuOpen = false;
                }
            } else if (controller.pendingRemoteMenuOpenTicks > 0) {
                controller.pendingRemoteMenuOpenTicks--;
            } else {
                controller.clearRemoteMenuValidationState();
                controller.relaxedRemoteMenu = null;
            }

            RtsRemoteMenuClientDiagnostics.observe(
                    minecraft,
                    controller.pendingRemoteMenuOpenTicks,
                    controller.screenlessRemoteMenuTicks);

            if (minecraft.screen == null
                    && !controller.suppressBuilderScreenRestoreUntilRtsRestart
                    && !hasRemoteMenuOpen
                    && controller.pendingRemoteMenuOpenTicks <= 0) {
                minecraft.setScreen(new BuilderScreen(controller));
            }

            controller.cameraOrbitService.tick(minecraft, controller.anchorX, controller.anchorY, controller.anchorZ, controller.maxRadius);
            boolean storageViewVisible = minecraft.screen instanceof BuilderScreen builderScreen
                    && builderScreen.isStorageViewVisible();
            controller.storageStateManager.tickStorageAutoRefresh(storageViewVisible);

            // Don't override player.input in RTS mode so the player entity can
            // properly respond to knockback and physics effects.
            // BuilderScreen intercepts input events preventing KeyMapping updates, but
            // the entity's own physics (knockback, gravity) are unaffected since
            // ServerPlayer's input is always null.
            // In RTS mode, prevent keyboard from controlling the player entity
            // (including jumping and sneaking).
            // isControlledCamera() is overridden by LocalPlayerMixin to return true,
            // so Minecraft's native sync mechanism handles position/rotation packets automatically.
            if (minecraft.player instanceof LocalPlayer localPlayer) {
                localPlayer.input.jumping = false;
                localPlayer.input.shiftKeyDown = false;
                localPlayer.input.forwardImpulse = 0.0F;
                localPlayer.input.leftImpulse = 0.0F;

                // RTS flight vertical control: when player is flying in RTS mode,
                // Ctrl+Space = ascend, Shift = descend (direct GLFW key state queries)
                if (localPlayer.getAbilities().flying) {
                    long window = minecraft.getWindow().getWindow();
                    boolean ctrlHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                    boolean spaceHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
                    boolean shiftHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

                    if (ctrlHeld && spaceHeld) {
                        double upSpeed = localPlayer.getAbilities().getFlyingSpeed() * 3.0;
                        localPlayer.setDeltaMovement(
                                localPlayer.getDeltaMovement().x,
                                upSpeed,
                                localPlayer.getDeltaMovement().z);
                    } else if (ctrlHeld && shiftHeld) {
                        double downSpeed = localPlayer.getAbilities().getFlyingSpeed() * 3.0;
                        localPlayer.setDeltaMovement(
                                localPlayer.getDeltaMovement().x,
                                -downSpeed,
                                localPlayer.getDeltaMovement().z);
                    }
                }
            }

        }

    boolean handleDeathScreenHandoff(Minecraft minecraft) {
            boolean dead = !minecraft.player.isAlive() || minecraft.player.isDeadOrDying();
            if (!dead) {
                return false;
            }

            controller.suppressBuilderScreenRestoreUntilRtsRestart = true;
            controller.homeSelectionMode = false;
            controller.pendingCraftTerminalOpen = false;
            controller.pendingCraftTerminalOpenTicks = 0;
            controller.pendingRemoteMenuOpenTicks = 0;
            controller.screenlessRemoteMenuTicks = 0;
            controller.miningOperationService.clearMiningRenderState();
            controller.clearRemoteMenuValidationState();
            RtsRemoteMenuClientDiagnostics.reset("player-death");

            if (minecraft.screen instanceof BuilderScreen
                    || minecraft.screen instanceof RtsHomeScreen
                    || minecraft.screen instanceof RtsCraftTerminalScreen) {
                minecraft.setScreen(null);
            }

            controller.cameraOrbitService.restorePreviousView(minecraft, minecraft.player);

            controller.enabled = false;
            controller.closeRangeAllowed = false;
            controller.cameraOrbitService.clearStateOnDeath();
            controller.cameraOrbitService.resetServerCameraEntityId();
            RtsClientPacketGateway.sendToggleCamera(false);
            return true;
        }

    void queuePanDrag(double dragX, double dragY) {
            controller.cameraOrbitService.queuePanDrag(dragX, dragY);
        }

    void queueRotateDrag(double dragX, double dragY) {
            controller.cameraOrbitService.queueRotateDrag(dragX, dragY);
        }

    void queueScroll(double scrollY) {
            controller.cameraOrbitService.queueScroll(scrollY);
        }

    void queueRotateQuarter(int direction) {
            controller.cameraOrbitService.queueRotateQuarter(direction);
        }

    void updateFunnelTarget(BlockPos target) {
            if (!controller.storageStateManager.isFunnelEnabled() || target == null) {
                return;
            }
            if (controller.funnelTargetCooldownTicks > 0) {
                return;
            }
            if (controller.lastFunnelTarget != null && controller.lastFunnelTarget.equals(target)) {
                return;
            }
            controller.lastFunnelTarget = target.immutable();
            controller.funnelTargetCooldownTicks = 2;
            RtsClientPacketGateway.sendFunnelTarget(controller.lastFunnelTarget);
        }

}
