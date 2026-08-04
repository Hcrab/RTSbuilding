package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.GridSlotRenderer.SLOT_SIZE;

public final class GridInputHandler {

    private static final int SLOT_GAP = 0;
    private static final int PAD_LEFT = 92;
    private static final int PAD_TOP = 2;
    private static final int GRID_TOP_OFFSET = 20;
    private static final int RIGHT_GAP = 18;
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_SPACING = 1;
    private static final int SEARCH_INPUT_H = 18;

    private final OverlayContext ctx;
    private final ScrollBar scrollBar;
    private final ScrollBar recentScrollBar;
    private final GridState state;
    private final TypeFilterPopup typeFilterPopup;
    private final ContainerModePopup containerModePopup;
    private final GridRenderer renderer;

    public GridInputHandler(OverlayContext ctx, ScrollBar scrollBar, ScrollBar recentScrollBar,
                     GridState state, TypeFilterPopup typeFilterPopup,
                     ContainerModePopup containerModePopup, GridRenderer renderer) {
        this.ctx = ctx;
        this.scrollBar = scrollBar;
        this.recentScrollBar = recentScrollBar;
        this.state = state;
        this.typeFilterPopup = typeFilterPopup;
        this.containerModePopup = containerModePopup;
        this.renderer = renderer;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return false;
        int x = ctx.getX(), y = ctx.getY(), h = ctx.getHeight();

        int typeFilterBtnX = calculateTypeFilterButtonX(x);
        int typeFilterBtnY = y + PAD_TOP + 1;
        int containerBtnX = calculateContainerButtonX(x);
        int containerBtnY = y + PAD_TOP + 1;

        if (typeFilterPopup.isOpen()) {
            boolean onToggleBtn = mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE
                    && mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE;
            if (!onToggleBtn && !typeFilterPopup.contains((int) mouseX, (int) mouseY)) {
                typeFilterPopup.close();
            }
        }
        if (containerModePopup.isOpen()) {
            boolean onToggleBtn = mouseX >= containerBtnX && mouseX < containerBtnX + BUTTON_SIZE
                    && mouseY >= containerBtnY && mouseY < containerBtnY + BUTTON_SIZE;
            if (!onToggleBtn && !containerModePopup.contains((int) mouseX, (int) mouseY)) {
                containerModePopup.close();
            }
        }

        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int localMainGridOriginX = x + PAD_LEFT;
        int localMainGridWidth = getCalcMainGridWidth();
        int barX = localMainGridOriginX + localMainGridWidth + 3;

        if (state.searchFocused) {
            int searchX = containerBtnX + BUTTON_SIZE + BUTTON_SPACING;
            int searchY = y + PAD_TOP + 1;
            int searchW = (localMainGridOriginX + localMainGridWidth) - searchX;
            boolean onSearch = searchW > SEARCH_INPUT_H
                    && mouseX >= searchX && mouseX < searchX + searchW
                    && mouseY >= searchY && mouseY < searchY + SEARCH_INPUT_H;
            if (!onSearch) {
                state.searchFocused = false;
                StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
                if (sm != null) {
                    sm.setSearch(state.searchBuffer.toString());
                }
            }
        }
        if (state.recentSearchFocused) {
            int recentSearchX = state.recentGridOriginX + BUTTON_SIZE + BUTTON_SPACING;
            int recentSearchY = y + PAD_TOP + 1;
            int recentSearchW = (x + 3 + state.recentCols * SLOT_SIZE) - recentSearchX;
            boolean onRecentSearch = recentSearchW > SEARCH_INPUT_H
                    && mouseX >= recentSearchX && mouseX < recentSearchX + recentSearchW
                    && mouseY >= recentSearchY && mouseY < recentSearchY + SEARCH_INPUT_H;
            if (!onRecentSearch) {
                state.recentSearchFocused = false;
            }
        }

        if (button != 0) {
            
            return handleRightClickActions(mouseX, mouseY, originY, localMainGridOriginX);
        }

        int itemDisplayX = x + PAD_LEFT;
        int itemDisplayY = y + PAD_TOP + 1;
        int itemDisplaySize = BUTTON_SIZE;

        if (mouseX >= itemDisplayX && mouseX < itemDisplayX + itemDisplaySize &&
            mouseY >= itemDisplayY && mouseY < itemDisplayY + itemDisplaySize) {
            renderer.scrollToSelectedItem();
            return true;
        }

        int sortBtnX = calculateSortButtonX(x);
        int sortBtnY = y + PAD_TOP + 1;

        if (mouseX >= sortBtnX && mouseX < sortBtnX + BUTTON_SIZE &&
            mouseY >= sortBtnY && mouseY < sortBtnY + BUTTON_SIZE) {
            cycleSortType();
            return true;
        }

        int orderBtnX = calculateOrderButtonX(x);
        int orderBtnY = y + PAD_TOP + 1;

        if (mouseX >= orderBtnX && mouseX < orderBtnX + BUTTON_SIZE &&
            mouseY >= orderBtnY && mouseY < orderBtnY + BUTTON_SIZE) {
            toggleSortOrder();
            return true;
        }

        if (mouseX >= typeFilterBtnX && mouseX < typeFilterBtnX + BUTTON_SIZE &&
            mouseY >= typeFilterBtnY && mouseY < typeFilterBtnY + BUTTON_SIZE) {
            typeFilterPopup.toggle();
            int screenWidth = Minecraft.getInstance().screen != null ? Minecraft.getInstance().screen.width : 0;
            typeFilterPopup.positionFromButtonAbove(typeFilterBtnX + BUTTON_SIZE / 2, typeFilterBtnY, screenWidth);
            return true;
        }

        if (mouseX >= containerBtnX && mouseX < containerBtnX + BUTTON_SIZE &&
            mouseY >= containerBtnY && mouseY < containerBtnY + BUTTON_SIZE) {
            containerModePopup.toggle();
            int screenWidth = Minecraft.getInstance().screen != null ? Minecraft.getInstance().screen.width : 0;
            containerModePopup.positionFromButtonAbove(containerBtnX + BUTTON_SIZE / 2, containerBtnY, screenWidth);
            return true;
        }

        int recentSortBtnX = state.recentGridOriginX;
        int recentSortBtnY = y + PAD_TOP + 1;
        if (mouseX >= recentSortBtnX && mouseX < recentSortBtnX + BUTTON_SIZE
                && mouseY >= recentSortBtnY && mouseY < recentSortBtnY + BUTTON_SIZE) {
            state.recentSortAscending = !state.recentSortAscending;
            return true;
        }

        int recentSearchX = state.recentGridOriginX + BUTTON_SIZE + BUTTON_SPACING;
        int recentSearchY = y + PAD_TOP + 1;
        int recentSearchW = (x + 3 + state.recentCols * SLOT_SIZE) - recentSearchX;
        if (recentSearchW > SEARCH_INPUT_H) {
            boolean clickedRecentSearch = mouseX >= recentSearchX && mouseX < recentSearchX + recentSearchW
                    && mouseY >= recentSearchY && mouseY < recentSearchY + SEARCH_INPUT_H;
            if (clickedRecentSearch) {
                state.searchFocused = false;
                state.recentSearchFocused = true;
                state.recentSearchCursorBlink = System.currentTimeMillis();
                return true;
            } else if (state.recentSearchFocused) {
                state.recentSearchFocused = false;
            }
        }

        int searchX = containerBtnX + BUTTON_SIZE + BUTTON_SPACING;
        int searchY = y + PAD_TOP + 1;
        int searchW = (localMainGridOriginX + localMainGridWidth) - searchX;
        if (searchW > SEARCH_INPUT_H) {
            boolean clickedSearch = mouseX >= searchX && mouseX < searchX + searchW
                    && mouseY >= searchY && mouseY < searchY + SEARCH_INPUT_H;
            if (clickedSearch) {
                state.recentSearchFocused = false;
                state.searchFocused = true;
                state.searchCursorBlink = System.currentTimeMillis();
                return true;
            } else if (state.searchFocused) {
                state.searchFocused = false;

                StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
                if (sm != null) {
                    sm.setSearch(state.searchBuffer.toString());
                }
            }
        }

        if (typeFilterPopup.isOpen() && typeFilterPopup.contains((int) mouseX, (int) mouseY)) {
            return typeFilterPopup.handleClick((int) mouseX, (int) mouseY);
        }

        if (containerModePopup.isOpen() && containerModePopup.contains((int) mouseX, (int) mouseY)) {
            return containerModePopup.handleClick((int) mouseX, (int) mouseY);
        }

        if (scrollBar.handleClick(mouseX, mouseY, barX,
                originY + 6, gridVisibleH - 12)) {
            return true;
        }

        int localRecentGridOriginX = x + 3;
        int localRecentGridW = state.recentCols * SLOT_SIZE;
        int recentBarX = localRecentGridOriginX + localRecentGridW + 3;
        if (recentScrollBar.handleClick(mouseX, mouseY, recentBarX,
                originY + 6, gridVisibleH - 12)) {
            return true;
        }

        if (!ctx.contains((int) mouseX, (int) mouseY)) return false;

        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return false;

        List<RecentEntry> recentItems = GridRenderer.getRecentItems(sm, state);
        if (!recentItems.isEmpty()) {
            int relRecentX = (int) mouseX - localRecentGridOriginX;
            int relRecentY = (int) mouseY - originY + recentScrollBar.getScroll();
            if (relRecentX >= 0 && relRecentY >= 0) {
                int recentCol = relRecentX / (SLOT_SIZE + SLOT_GAP);
                int recentRow = relRecentY / (SLOT_SIZE + SLOT_GAP);
                if (recentCol < state.recentCols && recentRow >= 0) {
                    int recentIdx = recentRow * state.recentCols + recentCol;
                    if (recentIdx < recentItems.size()) {
                        RecentEntry clickedRecent = recentItems.get(recentIdx);
                        if (!clickedRecent.preview().isEmpty()) {
                            boolean alreadySelected = ItemStack.isSameItemSameComponents(state.currentSelectedItem, clickedRecent.preview());
                            // 最近使用条目：走同一套“选取=启用”合并逻辑（最近网格无槽位索引，选中态统一为 -1）
                            state.selectedSlotIndex = -1;
                            if (alreadySelected) {
                                cancelSelection();
                            } else {
                                enableItem(clickedRecent.preview());
                            }
                        }
                        return true;
                    }
                }
            }
        }

        int localMainGridCols = getCalcMainCols();
        int localRows = getCalcRows();
        int relX = (int) mouseX - localMainGridOriginX;
        int relY = (int) mouseY - originY + scrollBar.getScroll();
        if (relX < 0 || relY < 0) {
            return tryReturnCarried();
        }
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col >= localMainGridCols) {
            return tryReturnCarried();
        }
        int idx = row * localMainGridCols + col;
        if (idx >= state.slotEntries.size()) {
            return tryReturnCarried();
        }

        int calculatedFrameHeight = localRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + calculatedFrameHeight;
        if (mouseY < originY || mouseY >= bottomY) {
            return tryReturnCarried();
        }

        // Shift+点击：原版式快速转移——一键放入打开的容器（服务端智能判定：
        // 容器打开时存入容器，仅背包菜单时转入玩家背包）；流体条目不参与，保持选材
        if (Screen.hasShiftDown()) {
            SlotEntry shiftEntry = state.slotEntries.get(idx);
            if (!shiftEntry.isFluid() && !shiftEntry.stack().isEmpty()) {
                // RTS 模式下禁止对“开启该模式的终端”进行拿去/快速转移
                if (CameraModule.isLockedTerminal(shiftEntry.stack())) return true;
                // 背包来源条目 → 存入绑定存储；存储条目 → 转入背包/打开的容器
                boolean fromInventory = shiftEntry.originalEntry() instanceof StorageEntry se && se.isPlayerInventory();
                RtsClientPacketGateway.sendLinkedQuickMove(shiftEntry.stack().copyWithCount(1), fromInventory);
                return true;
            }
        }

        // 原版式点击移动：点击条目 = 拿起（carried 空 → 提取一组跟随鼠标）或存回（carried 非空）
        SlotEntry clickedEntry = state.slotEntries.get(idx);
        if (!clickedEntry.isFluid() && !clickedEntry.stack().isEmpty()) {
            // RTS 模式下禁止对“开启该模式的终端”进行拿去/启用
            if (CameraModule.isLockedTerminal(clickedEntry.stack())) return true;
            Minecraft mc = Minecraft.getInstance();
            var menu = mc.player != null ? mc.player.containerMenu : null;
            if (menu != null) {
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) {
                    // 拿起：提取一组到 carried 立即跟随鼠标，并同步启用为建造选材（拿起就能用）
                    ItemStack prototype = clickedEntry.stack().copyWithCount(1);
                    int amount = Math.min(64, Math.max(1, (int) clickedEntry.count()));
                    // 背包来源条目 → 只从背包提取（所见即所得）；存储条目 → 存储优先提取
                    boolean fromInventory = clickedEntry.originalEntry() instanceof StorageEntry se && se.isPlayerInventory();
                    RtsClientPacketGateway.sendLinkedPickup(prototype, amount, fromInventory);
                    menu.setCarried(prototype.copyWithCount(amount)); // 乐观跟随，服务端权威经 S2C 同步
                    state.selectedSlotIndex = idx;
                    enableItem(clickedEntry.stack());
                } else {
                    // 携带中点击条目：全部放回网络（原版“点击放回”语义）
                    String itemId = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
                    RtsClientPacketGateway.sendReturnCarried(itemId, 64);
                    menu.setCarried(ItemStack.EMPTY);
                    // 放回的是当前启用选材 → 取消启用（启用只在“拿起”期间有效，放回即失效）
                    if (ItemStack.isSameItemSameComponents(carried, state.currentSelectedItem)) {
                        cancelSelection();
                    }
                }
                return true;
            }
        }
        // 流体条目：保持选材交互（选取/取消启用流体，不参与物品移动）
        return applyEntrySelection(idx);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!ctx.contains((int) mouseX, (int) mouseY)) return false;
        int recentRight = ctx.getX() + 3 + state.recentCols * SLOT_SIZE;
        int mainLeft = ctx.getX() + PAD_LEFT;
        int dividerX = (recentRight + mainLeft) / 2;
        if (mouseX < dividerX) {
            return recentScrollBar.handleScroll(scrollY);
        }
        return scrollBar.handleScroll(scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state.recentSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.recentSearchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (state.recentSearchCursorPos > 0 && state.recentSearchBuffer.length() > 0) {
                    state.recentSearchBuffer.deleteCharAt(state.recentSearchCursorPos - 1);
                    state.recentSearchCursorPos--;
                    state.recentSearchCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (state.recentSearchCursorPos < state.recentSearchBuffer.length()) {
                    state.recentSearchBuffer.deleteCharAt(state.recentSearchCursorPos);
                    state.recentSearchCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                state.recentSearchCursorPos = Math.max(0, state.recentSearchCursorPos - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                state.recentSearchCursorPos = Math.min(state.recentSearchBuffer.length(), state.recentSearchCursorPos + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                state.recentSearchCursorPos = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                state.recentSearchCursorPos = state.recentSearchBuffer.length();
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    state.recentSearchBuffer.insert(state.recentSearchCursorPos, clip);
                    state.recentSearchCursorPos += clip.length();
                    state.recentSearchCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            return true;
        }
        if (!state.searchFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applySearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            state.searchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (state.searchCursorPos > 0 && state.searchBuffer.length() > 0) {
                state.searchBuffer.deleteCharAt(state.searchCursorPos - 1);
                state.searchCursorPos--;
                state.searchCursorBlink = System.currentTimeMillis();
                updateSearch();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (state.searchCursorPos < state.searchBuffer.length()) {
                state.searchBuffer.deleteCharAt(state.searchCursorPos);
                state.searchCursorBlink = System.currentTimeMillis();
                updateSearch();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            state.searchCursorPos = Math.max(0, state.searchCursorPos - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            state.searchCursorPos = Math.min(state.searchBuffer.length(), state.searchCursorPos + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            state.searchCursorPos = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            state.searchCursorPos = state.searchBuffer.length();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                state.searchBuffer.insert(state.searchCursorPos, clip);
                state.searchCursorPos += clip.length();
                state.searchCursorBlink = System.currentTimeMillis();
                updateSearch();
            }
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (state.recentSearchFocused) {
            if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
                state.recentSearchBuffer.insert(state.recentSearchCursorPos, codePoint);
                state.recentSearchCursorPos++;
                state.recentSearchCursorBlink = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        if (!state.searchFocused) return false;
        if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
            state.searchBuffer.insert(state.searchCursorPos, codePoint);
            state.searchCursorPos++;
            state.searchCursorBlink = System.currentTimeMillis();
            updateSearch();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        if (recentScrollBar.isDragging()) {
            recentScrollBar.endDrag();
            return true;
        }
        // 点击式交互：拿起/存回均由 mouseClicked 完成，释放不再触发任何物品操作
        return false;
    }

    /**
     * 原版“点击空白放回”：携带物品时点击网格空白处 → 全部放回链接存储；
     * 未携带物品返回 false 不消费事件（交由其他面板处理）。
     */
    private boolean tryReturnCarried() {
        Minecraft mc = Minecraft.getInstance();
        var menu = mc.player != null ? mc.player.containerMenu : null;
        if (menu == null || menu.getCarried().isEmpty()) return false;
        ItemStack carried = menu.getCarried();
        String itemId = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        // amount=64：服务端取 min(amount, carried.getCount())，即全部存入；剩余由 S2C 同步回来
        RtsClientPacketGateway.sendReturnCarried(itemId, 64);
        menu.setCarried(ItemStack.EMPTY); // 乐观清空，服务端权威状态通过 S2CRtsCarriedSyncPayload 同步
        // 放回的是当前启用选材 → 取消启用（启用只在“拿起”期间有效，放回即失效）
        if (ItemStack.isSameItemSameComponents(carried, state.currentSelectedItem)) {
            cancelSelection();
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        int originY = ctx.getY() + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = ctx.getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
        if (scrollBar.isDragging()) {
            return scrollBar.handleDrag(mouseY, originY + 6, gridVisibleH - 12);
        }
        if (recentScrollBar.isDragging()) {
            return recentScrollBar.handleDrag(mouseY, originY + 6, gridVisibleH - 12);
        }
        // 点击式交互：拿起/放置均由 mouseClicked 完成，拖动不处理物品
        return false;
    }

    // ==================== 选取/启用合并逻辑 ====================

    /**
     * 原子取消：UI 选取态（槽位高亮 + 选中物品显示）与建造启用（BuildingModule）同步清空。
     * 所有“取消选材”路径（再点已选条目、右键移除最近条目、放回物品、退出 RTS 等）统一走这里，杜绝状态分裂。
     */
    public void cancelSelection() {
        state.selectedSlotIndex = -1;
        state.currentSelectedItem = ItemStack.EMPTY;
        BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        if (buildingModule != null) {
            buildingModule.clearSelection();
        }
    }

    /**
     * 原子启用物品：选中态（currentSelectedItem）与建造启用（selectItem + 最近使用记录）一次完成。
     */
    private void enableItem(ItemStack stack) {
        // RTS 模式下禁止对“开启该模式的终端”启用选材（覆盖最近条目点击路径）
        if (CameraModule.isLockedTerminal(stack)) return;
        state.currentSelectedItem = stack.copy();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId == null) return;
        String label = stack.getHoverName().getString();
        renderer.recordItemSelection(itemId, stack);
        BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        if (buildingModule != null) {
            buildingModule.selectItem(itemId, label, stack);
        }
    }

    /**
     * 原子启用流体：选中态与建造启用（selectFluid）一次完成。
     */
    private void enableFluid(SlotEntry entry) {
        if (!(entry.originalEntry() instanceof FluidEntry originalFluidEntry)) return;
        String fluidId = originalFluidEntry.fluidId();
        String label = entry.stack().getHoverName().getString();
        state.currentSelectedItem = entry.stack().copy();
        BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        if (buildingModule != null) {
            buildingModule.selectFluid(fluidId, label, entry.stack());
        }
    }

    /**
     * 主网格条目统一选取/取消：一次点击原子完成“UI 选取 + 物品启用”，
     * 再点同一条目则原子取消两者（取消选取即取消启用）。
     *
     * @return true 表示已处理（选取/取消完成）
     */
    private boolean applyEntrySelection(int idx) {
        SlotEntry entry = state.slotEntries.get(idx);
        // RTS 模式下禁止对“开启该模式的终端”进行启用选材
        if (CameraModule.isLockedTerminal(entry.stack())) return true;
        boolean isCurrent = state.selectedSlotIndex == idx
                || (state.selectedSlotIndex == -1
                        && ItemStack.isSameItemSameComponents(state.currentSelectedItem, entry.stack()));
        if (isCurrent) {
            cancelSelection();
            return true;
        }
        state.selectedSlotIndex = idx;
        if (!entry.isFluid()) {
            enableItem(entry.stack());
        } else {
            enableFluid(entry);
        }
        return true;
    }

    private void cycleSortType() {
        switch (state.currentSortType) {
            case NAME -> state.currentSortType = SortType.COUNT;
            case COUNT -> state.currentSortType = SortType.MOD;
            case MOD -> state.currentSortType = SortType.NAME;
        }
        state.slotEntriesDirty = true;
    }

    private void toggleSortOrder() {
        state.reverseSortOrder = !state.reverseSortOrder;
        state.slotEntriesDirty = true;
    }

    private void updateSearch() {
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm != null) {
            sm.setSearch(state.searchBuffer.toString());
        }
    }

    private void applySearch() {
        state.searchFocused = false;
        updateSearch();
    }

    private int calculateSortButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateOrderButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateTypeFilterButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int calculateContainerButtonX(int baseX) {
        return baseX + PAD_LEFT + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING + BUTTON_SIZE + BUTTON_SPACING;
    }

    private int getCalcMainCols() {
        return Math.max(1, (ctx.getWidth() - PAD_LEFT - RIGHT_GAP) / SLOT_SIZE);
    }

    private int getCalcMainGridWidth() {
        return getCalcMainCols() * SLOT_SIZE;
    }

    
    private boolean handleRightClickActions(double mouseX, double mouseY, int originY, int localMainGridOriginX) {
        int x = ctx.getX();
        int localRecentGridOriginX = x + 3;

        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return false;

        List<RecentEntry> recentItems = GridRenderer.getRecentItems(sm, state);
        if (recentItems.isEmpty()) return false;

        int relRecentX = (int) mouseX - localRecentGridOriginX;
        int relRecentY = (int) mouseY - originY + recentScrollBar.getScroll();
        if (relRecentX < 0 || relRecentY < 0) return false;

        int recentCol = relRecentX / (SLOT_SIZE + SLOT_GAP);
        int recentRow = relRecentY / (SLOT_SIZE + SLOT_GAP);
        if (recentCol >= state.recentCols || recentRow < 0) return false;

        int recentIdx = recentRow * state.recentCols + recentCol;
        if (recentIdx >= recentItems.size()) return false;

        RecentEntry clicked = recentItems.get(recentIdx);
        if (clicked.preview().isEmpty() || clicked.id() == null) return false;

        String removedId = clicked.id();
        state.itemSelectCounts.remove(removedId);
        state.itemSelectPreviews.remove(removedId);

        
        if (ItemStack.isSameItemSameComponents(state.currentSelectedItem, clicked.preview())) {
            cancelSelection();
        }

        
        sm.removeRecentEntry(removedId);
        return true;
    }

    private int getCalcRows() {
        return Math.max(1, (ctx.getHeight() - PAD_TOP - GRID_TOP_OFFSET) / SLOT_SIZE + 2);
    }
}
