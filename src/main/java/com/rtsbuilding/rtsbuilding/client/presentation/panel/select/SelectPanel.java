package com.rtsbuilding.rtsbuilding.client.presentation.panel.select;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;


public final class SelectPanel extends RtsPanel {

    

    
    private static final int PANEL_FIXED_W = 320;
    
    private static final int ICON_SIZE = 36;
    
    private static final int ITEM_W = 86;
    
    private static final int PAD_H = 6;
    
    private static final int PAD_V = 4;
    
    private static final int ICON_TEXT_GAP = 4;
    
    private static final int SCROLL_BAR_H = 8;
    
    private static final int SCROLL_GAP = 4;
    
    private static final int CONTENT_INSET = 4;
    
    private static final int TITLE_BAR_H = 20;
    
    private static final int TITLE_BAR_BOTTOM_GAP = 2;
    
    private static final int CONTENT_BOTTOM_PAD = 8;

    

    private final SelectionHighlight highlight;

    

    private List<SelectableEntry> entries;
    private final Vec3 rayOrigin;
    private final Vec3 rayDir;
    private final int contentItemH;
    private final int panelHeight;

    
    private final ScrollBar horizontalBar = new ScrollBar()
            .withOrientation(ScrollBar.Orientation.HORIZONTAL);

    
    private AnimFloat[] hoverStates;

    
    private final SelectFilterTabs filterTabs;

    
    private final int entityCount;
    private final int blockCount;

    
    public SelectPanel(List<SelectableEntry> entries, SelectionHighlight highlight,
                       Vec3 rayOrigin, Vec3 rayDir) {
        this.entries = List.copyOf(entries);
        this.highlight = Objects.requireNonNull(highlight, "highlight must not be null");
        this.rayOrigin = rayOrigin;
        this.rayDir = rayDir;
        initHoverStates(this.entries.size());

        
        int ec = 0, bc = 0;
        for (SelectableEntry e : this.entries) {
            if (e instanceof EntityEntry) ec++;
            else bc++;
        }
        this.entityCount = ec;
        this.blockCount = bc;

        this.filterTabs = new SelectFilterTabs();

        
        var font = Minecraft.getInstance().font;
        this.contentItemH = ICON_SIZE + ICON_TEXT_GAP + font.lineHeight;

        
        int contentH = SelectFilterTabs.TAB_BAR_H + PAD_V + contentItemH
                + SCROLL_GAP + SCROLL_BAR_H + PAD_V;
        this.panelHeight = TITLE_BAR_H + TITLE_BAR_BOTTOM_GAP + contentH + CONTENT_BOTTOM_PAD;

        
        this.closable = true;
        this.draggable = true;
        this.resizable = false;

        bounds.setInitialized(true);
    }

    

    private void initHoverStates(int count) {
        hoverStates = new AnimFloat[count];
        for (int i = 0; i < count; i++) {
            hoverStates[i] = AnimFloat.hover();
        }
    }

    

    
    public void updateEntries(List<SelectableEntry> newEntries) {
        List<SelectableEntry> safeCopy = List.copyOf(newEntries);
        AnimFloat[] newStates = new AnimFloat[safeCopy.size()];
        for (int i = 0; i < safeCopy.size(); i++) {
            SelectableEntry newEntry = safeCopy.get(i);
            int oldIdx = findEntryByIdentifier(newEntry.identifier());
            newStates[i] = oldIdx >= 0 ? hoverStates[oldIdx] : AnimFloat.hover();
        }
        this.entries = safeCopy;
        this.hoverStates = newStates;
    }

    
    private int findEntryByIdentifier(Object id) {
        for (int i = 0; i < entries.size(); i++) {
            if (Objects.equals(entries.get(i).identifier(), id)) {
                return i;
            }
        }
        return -1;
    }

    

    
    public SelectionHighlight getHighlight() {
        return highlight;
    }

    

    @Override
    protected Component getTitle() {
        return Component.literal("选择交互目标");
    }

    @Override
    public int getDefaultWidth() {
        return PANEL_FIXED_W;
    }

    @Override
    public int getDefaultHeight() {
        return panelHeight;
    }

    @Override
    public int getMinWindowWidth() {
        return PANEL_FIXED_W;
    }

    @Override
    public int getMinWindowHeight() {
        return panelHeight;
    }

    @Override
    public int getMaxWindowWidth() {
        return PANEL_FIXED_W;
    }

    @Override
    public int getMaxWindowHeight() {
        return panelHeight;
    }

    
    @Override
    protected int contentX() {
        return super.contentX() + CONTENT_INSET;
    }

    
    @Override
    protected int contentWidth() {
        return Math.max(0, super.contentWidth() - CONTENT_INSET * 2);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, PANEL_FIXED_W, panelHeight);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(PANEL_FIXED_W, panelHeight);
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) return;
        setWindowX(Math.max(8, (screen.width - getWindowWidth()) / 2));
        setWindowY(Mth.clamp((screen.height - getWindowHeight()) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, screen.height - getWindowHeight() - 8)));
    }

    @Override
    public int getTitleBarHeight() {
        return TITLE_BAR_H;
    }

    @Override
    protected boolean shouldClipContent() {
        return true;
    }

    

    private int computeEntryWidth(SelectableEntry entry) {
        int textW = Minecraft.getInstance().font.width(entry.displayName());
        return Math.max(ITEM_W, textW + 4);
    }

    

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        
        highlight.clear();

        
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        
        int filterOffset = filterTabs.getFilterOffset(entityCount, blockCount);
        if (filterTabs.hasMixedTypes(entityCount, blockCount)) {
            filterTabs.render(g, mouseX, mouseY, cx, cy, cw, entityCount, blockCount, entries.size());
        }

        int scrollOffset = horizontalBar.getScroll();
        int visibleW = Math.max(1, cw - PAD_H * 2);

        
        int totalW = 0;
        for (SelectableEntry e : entries) {
            if (filterTabs.matchesFilter(e) && hasGuiInteraction(e)) {
                totalW += computeEntryWidth(e);
            }
        }
        horizontalBar.setContent(totalW, visibleW);

        
        int sbY = cy + filterOffset + PAD_V + contentItemH + SCROLL_GAP + 7;
        int sbLen = cw - PAD_H * 2;

        
        horizontalBar.render(g, cx + PAD_H, sbY, sbLen);

        
        int currentX = cx + PAD_H - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            SelectableEntry entry = entries.get(i);
            if (!filterTabs.matchesFilter(entry) || !hasGuiInteraction(entry)) continue;

            int entryW = computeEntryWidth(entry);
            int itemX = currentX;
            int itemY = cy + filterOffset + PAD_V + 2;
            int ih = contentItemH;

            if (itemX + entryW >= cx && itemX <= cx + cw) {
                boolean isHovered = mouseX >= itemX && mouseX < itemX + entryW
                        && mouseY >= itemY - 5 && mouseY < itemY + ih + 5;
                float t = hoverStates[i].track(isHovered);

                
                SelectEntryRenderer.renderEntryBg(g, itemX, itemY - 5, entryW, ih + 10, t);

                
                if (isHovered) {
                    updateHighlight(entry);
                }

                
                SelectEntryRenderer.renderEntryContent(g, entry, itemX, itemY, entryW,
                        ICON_SIZE, ICON_TEXT_GAP, isHovered);
            }
            currentX += entryW;
        }

        
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    

    
    private static boolean hasGuiInteraction(SelectableEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        return switch (entry) {
            case EntityEntry ee -> hasEntityGui(ee.entity());
            case BlockEntry be -> hasBlockGui(mc, be.blockPos());
        };
    }

    private static boolean hasEntityGui(@Nullable Entity entity) {
        if (entity == null || !entity.isAlive()) return false;

        
        if (entity instanceof AbstractVillager) {
            if (entity instanceof Villager villager) {
                return villager.getVillagerData().getProfession() != VillagerProfession.NONE;
            }
            return true; 
        }
        
        if (entity instanceof AbstractHorse) return true;
        
        if (entity instanceof ContainerEntity) return true;
        
        if (entity instanceof MenuProvider) return true;
        return false;
    }

    
    private static final Map<Class<?>, Boolean> USE_OVERRIDE_CACHE = new ConcurrentHashMap<>();

    private static boolean hasBlockGui(Minecraft mc, BlockPos blockPos) {
        
        BlockState state = mc.level.getBlockState(blockPos);
        if (state.getMenuProvider(mc.level, blockPos) != null) return true;
        
        BlockEntity be = mc.level.getBlockEntity(blockPos);
        if (be instanceof MenuProvider) {
            
            if (be instanceof LecternBlockEntity lectern && lectern.getBook().isEmpty()) return false;
            return true;
        }
        
        return hasUseOverride(state.getBlock());
    }

    
    private static boolean hasUseOverride(net.minecraft.world.level.block.Block block) {
        Class<?> clazz = block.getClass();
        if (clazz == net.minecraft.world.level.block.Block.class) return false;
        return USE_OVERRIDE_CACHE.computeIfAbsent(clazz, c -> {
            Class<?> current = c;
            while (current != net.minecraft.world.level.block.Block.class && current != null) {
                try {
                    current.getDeclaredMethod("use",
                            net.minecraft.world.level.block.state.BlockState.class,
                            net.minecraft.world.level.Level.class,
                            BlockPos.class,
                            net.minecraft.world.entity.player.Player.class,
                            net.minecraft.world.InteractionHand.class,
                            BlockHitResult.class);
                    return true;
                } catch (NoSuchMethodException e) {
                    try {
                        current.getDeclaredMethod("useWithoutItem",
                                net.minecraft.world.level.block.state.BlockState.class,
                                net.minecraft.world.level.Level.class,
                                BlockPos.class,
                                net.minecraft.world.entity.player.Player.class,
                                BlockHitResult.class);
                        return true;
                    } catch (NoSuchMethodException e2) {
                        
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        });
    }

    
    private void updateHighlight(SelectableEntry entry) {
        switch (entry) {
            case EntityEntry ee -> highlight.set(ee.entity(), null);
            case BlockEntry be -> highlight.set(null, be.blockHit());
        }
    }

    

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int scrollOffset = horizontalBar.getScroll();
        int filterOffset = filterTabs.getFilterOffset(entityCount, blockCount);

        
        if (filterTabs.hasMixedTypes(entityCount, blockCount)
                && filterTabs.handleClick(mouseX, mouseY, cx, cy,
                entityCount, blockCount, entries.size())) {
            return;
        }

        
        int sbY = cy + filterOffset + PAD_V + contentItemH + SCROLL_GAP + 7;
        int sbLen = cw - PAD_H * 2;
        if (horizontalBar.handleClick(mouseX, mouseY, cx + PAD_H, sbY, sbLen)) {
            return;
        }

        
        int clickX = cx + PAD_H - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            SelectableEntry entry = entries.get(i);
            if (!filterTabs.matchesFilter(entry) || !hasGuiInteraction(entry)) continue;

            int entryW = computeEntryWidth(entry);
            int itemX = clickX;
            int itemY = cy + filterOffset + PAD_V + 2;
            int ih = contentItemH;
            boolean inside = (int) mouseX >= itemX && (int) mouseX < itemX + entryW
                    && (int) mouseY >= itemY - 5 && (int) mouseY < itemY + ih + 5;
            if (inside) {
                interactWithEntry(i);
                return;
            }
            clickX += entryW;
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return horizontalBar.handleScroll(scrollY);
    }

    

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (horizontalBar.isDragging()) {
            int cx = contentX();
            int cw = contentWidth();
            int filterOffset = filterTabs.getFilterOffset(entityCount, blockCount);
            int sbY = contentY() + filterOffset + PAD_V + contentItemH + SCROLL_GAP + 7;
            int sbLen = cw - PAD_H * 2;
            horizontalBar.handleDrag(mouseX, cx + PAD_H, sbLen);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (horizontalBar.isDragging()) {
            horizontalBar.endDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    

    private void interactWithEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        SelectableEntry entry = entries.get(index);
        switch (entry) {
            case EntityEntry ee -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    ee.entityId(), ee.hitLocation(), null, rayOrigin, rayDir);
            case BlockEntry be -> RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    C2SRtsInteractPayload.NO_ENTITY,
                    be.hitLocation(), be.blockHit(), rayOrigin, rayDir);
        }
        setOpen(false);
    }

    

    @Override
    protected void onClose() {
        highlight.clear();
    }

    

    public Vec3 getRayOrigin() {
        return rayOrigin;
    }

    public Vec3 getRayDir() {
        return rayDir;
    }

    public List<SelectableEntry> getEntries() {
        return entries;
    }
}
