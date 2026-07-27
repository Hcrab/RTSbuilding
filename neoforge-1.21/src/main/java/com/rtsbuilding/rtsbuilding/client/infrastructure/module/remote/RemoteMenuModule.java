package com.rtsbuilding.rtsbuilding.client.infrastructure.module.remote;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;

public final class RemoteMenuModule implements FeatureModule {

    
    
    

    
    private static final int REMOTE_MENU_OPEN_GRACE_TICKS = 80;
    
    private static final int SCREENLESS_REMOTE_MENU_RECOVERY_TICKS = 10;

    
    
    

    
    private int pendingRemoteMenuOpenTicks;
    
    private int screenlessRemoteMenuTicks;
    
    private AbstractContainerMenu relaxedRemoteMenu;
    
    private boolean hasRemoteMenuOpen;
    
    private boolean wasRemoteMenuOpen;

    
    private boolean pendingCraftTerminalOpen;
    
    private int pendingCraftTerminalOpenTicks;

    
    
    

    @Override
    public String moduleId() {
        return "remote_menu";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) {
                
                clearRemoteMenuValidationState();
                this.relaxedRemoteMenu = null;
                this.pendingCraftTerminalOpen = false;
                this.pendingCraftTerminalOpenTicks = 0;
                this.pendingRemoteMenuOpenTicks = 0;
                this.screenlessRemoteMenuTicks = 0;
                this.hasRemoteMenuOpen = false;
                this.wasRemoteMenuOpen = false;
            }
        } else if (event instanceof StateEvent.PlayerDied) {
            clearRemoteMenuValidationState();
            this.relaxedRemoteMenu = null;
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
            this.pendingRemoteMenuOpenTicks = 0;
            this.screenlessRemoteMenuTicks = 0;
            this.hasRemoteMenuOpen = false;
            this.wasRemoteMenuOpen = false;
        }
        
        
    }

    @Override
    public void tick(long epochMs, int tickIndex) {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        
        boolean remoteMenuOpen = mc.player.containerMenu != null
                && mc.player.containerMenu.containerId != 0;

        
        
        if (remoteMenuOpen && mc.screen == null && this.pendingRemoteMenuOpenTicks <= 0) {
            this.screenlessRemoteMenuTicks++;
            if (this.screenlessRemoteMenuTicks >= SCREENLESS_REMOTE_MENU_RECOVERY_TICKS) {
                
                RtsClientPacketGateway.sendCloseRemoteMenu();
                mc.player.closeContainer();
                clearRemoteMenuValidationState();
                this.relaxedRemoteMenu = null;
                remoteMenuOpen = false;
                this.screenlessRemoteMenuTicks = 0;
            }
        } else {
            this.screenlessRemoteMenuTicks = 0;
        }

        
        
        if (this.pendingCraftTerminalOpen
                && mc.player.containerMenu instanceof CraftingMenu
                && mc.player.containerMenu.containerId != 0
                && !(mc.screen instanceof RtsCraftTerminalScreen)) {
            Component pendingTitle = mc.screen != null
                    ? mc.screen.getTitle()
                    : Component.literal("RTS Craft Terminal");
            mc.setScreen(new RtsCraftTerminalScreen(
                    mc.player.containerMenu, mc.player.getInventory(), pendingTitle));
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
        }

        
        if (mc.screen instanceof CraftingScreen craftingScreen
                && mc.player != null
                && craftingScreen.getMenu() instanceof CraftingMenu
                && !(mc.screen instanceof RtsCraftTerminalScreen)
                && shouldUseRtsCraftTerminalScreen(craftingScreen)) {
            mc.setScreen(new RtsCraftTerminalScreen(
                    craftingScreen.getMenu(),
                    mc.player.getInventory(),
                    craftingScreen.getTitle()));
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
        } else if (this.pendingCraftTerminalOpen) {
            if (this.pendingCraftTerminalOpenTicks > 0) {
                this.pendingCraftTerminalOpenTicks--;
            } else {
                this.pendingCraftTerminalOpen = false;
            }
        }

        
        if (remoteMenuOpen) {
            this.pendingRemoteMenuOpenTicks = 0;
            try {
                AbstractContainerMenu activeRemoteMenu = RtsClientRemoteMenuCompat.install(
                        mc, mc.player.containerMenu);
                if (this.relaxedRemoteMenu != activeRemoteMenu) {
                    RtsClientRemoteMenuCompat.relaxValidation(activeRemoteMenu);
                    this.relaxedRemoteMenu = activeRemoteMenu;
                }
                
                
                
                
                if (mc.screen instanceof BuilderScreen) {
                    
                }
            } catch (Throwable throwable) {
                handleRemoteMenuOpenFailure(mc, throwable);
                remoteMenuOpen = false;
            }
        } else if (this.pendingRemoteMenuOpenTicks > 0) {
            this.pendingRemoteMenuOpenTicks--;
        } else {
            clearRemoteMenuValidationState();
            this.relaxedRemoteMenu = null;
        }

        
        this.hasRemoteMenuOpen = remoteMenuOpen;
        if (this.hasRemoteMenuOpen != this.wasRemoteMenuOpen) {
            this.wasRemoteMenuOpen = this.hasRemoteMenuOpen;
            if (this.hasRemoteMenuOpen) {
                kernel().dispatch(new StateEvent.RemoteMenuOpened());
            } else {
                kernel().dispatch(new StateEvent.RemoteMenuClosed());
            }
        }
    }

    
    
    

    
    public void beginRemoteMenuOpenGrace() {
        this.pendingRemoteMenuOpenTicks = Math.max(
                this.pendingRemoteMenuOpenTicks, REMOTE_MENU_OPEN_GRACE_TICKS);
        this.screenlessRemoteMenuTicks = 0;
        RtsRemoteMenuCompat.beginClientRemoteMenuOpen();
    }

    
    public void openCraftTerminal() {
        this.pendingCraftTerminalOpen = true;
        this.pendingCraftTerminalOpenTicks = 120;
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendOpenCraftTerminal();
    }

    
    public boolean isRemoteMenuOpen() {
        return this.hasRemoteMenuOpen;
    }

    
    public boolean isRemoteMenuPending() {
        return this.pendingRemoteMenuOpenTicks > 0;
    }

    
    
    

    
    private void handleRemoteMenuOpenFailure(Minecraft minecraft, Throwable throwable) {
        String menuClass = minecraft.player != null && minecraft.player.containerMenu != null
                ? minecraft.player.containerMenu.getClass().getName()
                : "null";
        String screenClass = minecraft.screen != null
                ? minecraft.screen.getClass().getName()
                : "null";
        RtsbuildingMod.LOGGER.error(
                "RTS remote menu open failed for menu {} on screen {}; closing container to prevent a client crash.",
                menuClass, screenClass, throwable);
        clearRemoteMenuValidationState();
        this.pendingRemoteMenuOpenTicks = 0;
        if (minecraft.player != null) {
            RtsClientPacketGateway.sendCloseRemoteMenu();
            minecraft.player.closeContainer();
            minecraft.player.displayClientMessage(Component.literal("Open failed."), true);
        }
        minecraft.setScreen(null);
    }

    
    private void clearRemoteMenuValidationState() {
        this.relaxedRemoteMenu = null;
        RtsRemoteMenuCompat.clearClientRemoteMenu();
    }

    
    private boolean shouldUseRtsCraftTerminalScreen(CraftingScreen craftingScreen) {
        if (this.pendingCraftTerminalOpen) {
            return true;
        }
        return craftingScreen.getTitle() != null
                && "RTS Craft Terminal".equals(craftingScreen.getTitle().getString());
    }

    private RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }
}
