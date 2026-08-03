package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsClientTraceTracker;
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
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.block.state.IBlockState;
import org.lwjgl.input.Keyboard;

import java.util.List;

final class ClientRtsLifecycleOwner {
    private final ClientRtsController controller;

    ClientRtsLifecycleOwner(ClientRtsController controller) {
        this.controller = controller;
    }

    void applyServerCameraState(S2CRtsCameraStatePayload payload) {
            Minecraft minecraft = Minecraft.getMinecraft();

            if (payload.enabled()) {
                boolean freshEnable = !controller.enabled;
                if (freshEnable) {
                    RtsClientTraceTracker.reset("RTS_ENABLED");
                }
                controller.enabled = true;
                controller.cameraOrbitService.setServerCameraEntityId(payload.cameraEntityId());
                controller.anchorX = payload.anchorX();
                controller.anchorY = payload.anchorY();
                controller.anchorZ = payload.anchorZ();
                controller.maxRadius = payload.maxRadius();
                controller.homeSelectionMode = payload.homeSelection();
                controller.closeRangeAllowed = payload.closeRangeAllowed();

                if (freshEnable) {
                    controller.cameraOrbitService.capturePreviousView(minecraft);
                    // Clear stale player input to prevent WASD presses from before entering RTS mode from affecting movement
                    EntityPlayerSP localPlayer = minecraft.player;
                    if (localPlayer != null) {
                        localPlayer.movementInput.moveForward = 0.0F;
                        localPlayer.movementInput.moveStrafe = 0.0F;
                        localPlayer.movementInput.jump = false;
                        localPlayer.movementInput.sneak = false;
                    }
                }

                controller.cameraOrbitService.applyRtsView(minecraft);

                if (!(minecraft.currentScreen instanceof BuilderScreen)) {
                    minecraft.displayGuiScreen(new BuilderScreen(controller));
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
            RtsClientTraceTracker.reset("RTS_DISABLED");
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

            if (minecraft.currentScreen instanceof BuilderScreen) {
                minecraft.displayGuiScreen(null);
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
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.player == null || !controller.enabled) {
                controller.clearRemoteMenuValidationState();
            }
        }

    void tick() {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (!controller.enabled) {
                controller.suppressBuilderScreenRestoreUntilRtsRestart = false;
                return;
            }

            if (minecraft.player == null || minecraft.world == null) {
                return;
            }
            if (controller.handleDeathScreenHandoff(minecraft)) {
                return;
            }

            if (controller.funnelTargetCooldownTicks > 0) {
                controller.funnelTargetCooldownTicks--;
            }

            boolean hasRemoteMenuOpen = minecraft.player.openContainer != null
                    && minecraft.player.openContainer.windowId != 0;

            if (hasRemoteMenuOpen
                    && minecraft.currentScreen == null
                    && controller.pendingRemoteMenuOpenTicks <= 0) {
                controller.screenlessRemoteMenuTicks++;
                if (controller.screenlessRemoteMenuTicks == 1) {
                    RtsClientTraceTracker.screenMissing(
                            minecraft.player.openContainer.windowId,
                            minecraft.player.openContainer.getClass().getName(),
                            ClientRtsController.SCREENLESS_REMOTE_MENU_RECOVERY_TICKS);
                }
                if (controller.screenlessRemoteMenuTicks >= ClientRtsController.SCREENLESS_REMOTE_MENU_RECOVERY_TICKS) {
                    RtsClientTraceTracker.screenlessRecovery(
                            minecraft.player.openContainer.windowId,
                            minecraft.player.openContainer.getClass().getName());
                    RtsClientPacketGateway.sendCloseRemoteMenu();
                    minecraft.player.closeScreen();
                    controller.clearRemoteMenuValidationState();
                    controller.relaxedRemoteMenu = null;
                    hasRemoteMenuOpen = false;
                    controller.screenlessRemoteMenuTicks = 0;
                }
            } else {
                controller.screenlessRemoteMenuTicks = 0;
            }

            if (controller.pendingCraftTerminalOpen
                    && minecraft.player.openContainer instanceof ContainerWorkbench
                    && minecraft.player.openContainer.windowId != 0
                    && !(minecraft.currentScreen instanceof RtsCraftTerminalScreen)) {
                ContainerWorkbench pendingMenu = (ContainerWorkbench) minecraft.player.openContainer;
                minecraft.displayGuiScreen(new RtsCraftTerminalScreen(pendingMenu, minecraft.player.inventory,
                        new TextComponentString("RTS Craft Terminal")));
                controller.pendingCraftTerminalOpen = false;
                controller.pendingCraftTerminalOpenTicks = 0;
            }

            if (minecraft.currentScreen instanceof GuiCrafting
                    && minecraft.player != null
                    && ((GuiCrafting) minecraft.currentScreen).inventorySlots instanceof ContainerWorkbench
                    && !(minecraft.currentScreen instanceof RtsCraftTerminalScreen)) {
                GuiCrafting craftingScreen = (GuiCrafting) minecraft.currentScreen;
                if (controller.shouldUseRtsCraftTerminalScreen(craftingScreen)) {
                    ContainerWorkbench craftingMenu = (ContainerWorkbench) craftingScreen.inventorySlots;
                    minecraft.displayGuiScreen(new RtsCraftTerminalScreen(craftingMenu, minecraft.player.inventory,
                            new TextComponentString("RTS Craft Terminal")));
                    controller.pendingCraftTerminalOpen = false;
                    controller.pendingCraftTerminalOpenTicks = 0;
                }
            }
            if (controller.pendingCraftTerminalOpen) {
                if (controller.pendingCraftTerminalOpenTicks > 0) {
                    controller.pendingCraftTerminalOpenTicks--;
                } else {
                    controller.pendingCraftTerminalOpen = false;
                }
            }

            if (hasRemoteMenuOpen) {
                controller.pendingRemoteMenuOpenTicks = 0;
                try {
                    Container activeRemoteMenu = RtsClientRemoteMenuCompat.install(minecraft, minecraft.player.openContainer);
                    if (controller.relaxedRemoteMenu != activeRemoteMenu) {
                        RtsClientTraceTracker.menuInstalled(
                                activeRemoteMenu.windowId,
                                activeRemoteMenu.getClass().getName(),
                                minecraft.currentScreen == null
                                        ? "null" : minecraft.currentScreen.getClass().getName());
                        RtsClientRemoteMenuCompat.relaxValidation(activeRemoteMenu);
                        controller.relaxedRemoteMenu = activeRemoteMenu;
                    }
                    if (minecraft.currentScreen instanceof BuilderScreen) {
                        // First-open GUI construction can leave a brief null-screen handoff. Once a real
                        // container menu exists, let it take over instead of keeping BuilderScreen active.
                        RtsClientTraceTracker.builderHandoff(
                                activeRemoteMenu.windowId,
                                activeRemoteMenu.getClass().getName());
                        minecraft.displayGuiScreen(null);
                    }
                } catch (Throwable throwable) {
                    controller.handleRemoteMenuOpenFailure(minecraft, throwable);
                    hasRemoteMenuOpen = false;
                }
            } else if (controller.pendingRemoteMenuOpenTicks > 0) {
                controller.pendingRemoteMenuOpenTicks--;
                if (controller.pendingRemoteMenuOpenTicks == 0) {
                    RtsClientTraceTracker.hintTimeout(
                            minecraft.player.openContainer == null
                                    ? "null" : minecraft.player.openContainer.getClass().getName(),
                            minecraft.currentScreen == null
                                    ? "null" : minecraft.currentScreen.getClass().getName());
                }
            } else {
                if (controller.relaxedRemoteMenu != null) {
                    RtsClientTraceTracker.menuClosed(
                            controller.relaxedRemoteMenu.windowId,
                            controller.relaxedRemoteMenu.getClass().getName(),
                            minecraft.currentScreen == null
                                    ? "null" : minecraft.currentScreen.getClass().getName());
                }
                controller.clearRemoteMenuValidationState();
                controller.relaxedRemoteMenu = null;
            }

            if (minecraft.currentScreen == null
                    && !controller.suppressBuilderScreenRestoreUntilRtsRestart
                    && !hasRemoteMenuOpen
                    && controller.pendingRemoteMenuOpenTicks <= 0) {
                minecraft.displayGuiScreen(new BuilderScreen(controller));
            }

            controller.cameraOrbitService.tick(minecraft, controller.anchorX, controller.anchorY, controller.anchorZ, controller.maxRadius);
            boolean storageViewVisible = minecraft.currentScreen instanceof BuilderScreen
                    && ((BuilderScreen) minecraft.currentScreen).isStorageViewVisible();
            controller.storageStateManager.tickStorageAutoRefresh(storageViewVisible);

            // Don't override player.input in RTS mode so the player entity can
            // properly respond to knockback and physics effects.
            // BuilderScreen intercepts input events preventing KeyMapping updates, but
            // the entity's own physics (knockback, gravity) are unaffected since
            // ServerPlayer's input is always null.
            // In RTS mode, prevent keyboard from controlling the player entity
            // (including jumping and sneaking).
            // 1.12 的 isCurrentViewEntity() 由 LocalPlayerMixin 在 RTS 模式下返回 true，
            // so Minecraft's native sync mechanism handles position/rotation packets automatically.
            EntityPlayerSP localPlayer = minecraft.player;
            if (localPlayer != null) {
                localPlayer.movementInput.jump = false;
                localPlayer.movementInput.sneak = false;
                localPlayer.movementInput.moveForward = 0.0F;
                localPlayer.movementInput.moveStrafe = 0.0F;

                // RTS flight vertical control: when player is flying in RTS mode,
                // Ctrl+Space = ascend, Shift = descend (direct GLFW key state queries)
                if (localPlayer.capabilities.isFlying) {
                    boolean ctrlHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                            || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                    boolean spaceHeld = Keyboard.isKeyDown(Keyboard.KEY_SPACE);
                    boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

                    if (ctrlHeld && spaceHeld) {
                        double upSpeed = localPlayer.capabilities.getFlySpeed() * 3.0;
                        localPlayer.motionY = upSpeed;
                    } else if (ctrlHeld && shiftHeld) {
                        double downSpeed = localPlayer.capabilities.getFlySpeed() * 3.0;
                        localPlayer.motionY = -downSpeed;
                    }
                }
            }

        }

    boolean handleDeathScreenHandoff(Minecraft minecraft) {
            boolean dead = minecraft.player == null || !minecraft.player.isEntityAlive() || minecraft.player.isDead;
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

            if (minecraft.currentScreen instanceof BuilderScreen
                    || minecraft.currentScreen instanceof RtsHomeScreen
                    || minecraft.currentScreen instanceof RtsCraftTerminalScreen) {
                minecraft.displayGuiScreen(null);
            }

            controller.cameraOrbitService.restorePreviousView(minecraft, minecraft.player);

            controller.enabled = false;
            RtsClientTraceTracker.reset("PLAYER_DEAD");
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
            controller.lastFunnelTarget = new BlockPos(target);
            controller.funnelTargetCooldownTicks = 2;
            RtsClientPacketGateway.sendFunnelTarget(controller.lastFunnelTarget);
        }

}
