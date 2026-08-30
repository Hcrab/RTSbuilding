package com.rtsbuilding.rtsbuilding.server.service.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsLinkedItemInteractorTest {
    @Test
    void selectedStorageItemCanComeFromLinkedStoragePlayerInventoryViewOrCreativeMode() {
        assertTrue(RtsLinkedItemInteractor.canUseSelectedItemSource(true, false, false),
                "真实链接储存里的选中物品应可用于右键交互/放置。");
        assertTrue(RtsLinkedItemInteractor.canUseSelectedItemSource(false, true, false),
                "底部储存页只显示玩家背包时，选中物品也应可用于右键交互/放置。");
        assertTrue(RtsLinkedItemInteractor.canUseSelectedItemSource(false, false, true),
                "创造模式物品栏选中的物品不应被库存检测拦下。");
        assertFalse(RtsLinkedItemInteractor.canUseSelectedItemSource(false, false, false),
                "没有任何可用来源时才应该拒绝选中物品。");
    }
}
