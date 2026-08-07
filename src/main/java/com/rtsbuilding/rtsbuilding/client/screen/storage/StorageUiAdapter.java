package com.rtsbuilding.rtsbuilding.client.screen.storage;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiAction;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiState;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.1 已绑定仓库窗口的生产适配层。
 *
 * <p>只转换当前可见行，避免大仓库逐帧复制；服务端仍是绑定、更新与解绑的唯一权威，
 * 本类不会把客户端 UI 变成第二个仓库解析器。</p>
 */
final class StorageUiAdapter {
    private StorageUiAdapter() {
    }

    static StorageUiState snapshot(
            ClientRtsController controller,
            boolean open,
            int scroll,
            int capacity) {
        List<LinkedStorageEntry> entries = controller.getLinkedStorageEntries();
        int max = Math.max(0, entries.size() - Math.max(1, capacity));
        int safeScroll = Math.max(0, Math.min(scroll, max));
        List<StorageUiEntry> visible = new ArrayList<>();
        for (int index = safeScroll;
                index < Math.min(entries.size(), safeScroll + capacity);
                index++) {
            visible.add(toCore(entries.get(index)));
        }
        StorageUiStatus status = controller.isStorageScanRunning()
                ? StorageUiStatus.LOADING
                : entries.isEmpty() ? StorageUiStatus.EMPTY : StorageUiStatus.READY;
        return new StorageUiState(open, status, entries.size(), safeScroll,
                capacity, visible, "");
    }

    static StorageUiTransition dispatch(
            ClientRtsController controller,
            StorageUiState state,
            StorageUiAction action) {
        StorageUiTransition transition = StorageUiReducer.apply(state, action);
        if (transition.command == StorageUiTransition.Command.SCROLL
                || transition.command == StorageUiTransition.Command.NONE) {
            return transition;
        }
        LinkedStorageEntry entry = find(controller, action.stableKey);
        if (entry == null) {
            return transition;
        }
        if (transition.command == StorageUiTransition.Command.SET_PRIORITY) {
            controller.updateLinkedStorageSettings(entry.dimensionId(), entry.pos(),
                    entry.mode() == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY, action.value);
        } else if (transition.command == StorageUiTransition.Command.TOGGLE_EXTRACT) {
            controller.updateLinkedStorageSettings(entry.dimensionId(), entry.pos(),
                    entry.mode() != C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY, entry.priority());
        } else if (transition.command == StorageUiTransition.Command.UNLINK) {
            controller.unlinkLinkedStorage(entry.dimensionId(), entry.pos());
        }
        return transition;
    }

    static String key(LinkedStorageEntry entry) {
        BlockPos position = entry == null ? null : entry.pos();
        String dimension = entry == null || entry.dimensionId() == null ? "" : entry.dimensionId();
        return position == null ? "" : dimension + "|" + position.getX() + ","
                + position.getY() + "," + position.getZ();
    }

    private static LinkedStorageEntry find(ClientRtsController controller, String key) {
        for (LinkedStorageEntry entry : controller.getLinkedStorageEntries()) {
            if (key(entry).equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private static StorageUiEntry toCore(LinkedStorageEntry entry) {
        ItemStack preview = entry.preview();
        Identifier itemId = preview == null || preview.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getKey(preview.getItem());
        BlockPos position = entry.pos();
        String coordinates = position == null ? "? ? ?"
                : position.getX() + ", " + position.getY() + ", " + position.getZ();
        String dimension = entry.dimensionId() == null || entry.dimensionId().isBlank()
                ? "?" : entry.dimensionId();
        String displayPosition = entry.worldAvailable()
                ? dimension + " · " + coordinates
                : dimension + " · N/A";
        return new StorageUiEntry(key(entry), entry.label(), displayPosition, entry.priority(),
                entry.mode() == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY,
                entry.worldAvailable(), itemId == null ? "" : itemId.toString());
    }
}
