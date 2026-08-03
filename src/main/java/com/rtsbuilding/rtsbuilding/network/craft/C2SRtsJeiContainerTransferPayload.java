package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把标准 JEI/HEI 机器输入槽与其可接受原型发送给服务端。
 *
 * <p>客户端槽号只是一份请求提示。服务端仍会核对当前窗口、槽位边界、玩家背包归属、
 * {@code Slot.isItemValid} 与链接存储权限。集合与备选项均有硬上限，避免恶意包造成无界分配。</p>
 */
public final class C2SRtsJeiContainerTransferPayload implements IMessage {
    public static final int MAX_INPUTS = 36;
    public static final int MAX_ALTERNATIVES_PER_INPUT = 16;
    public static final int MAX_TOTAL_ALTERNATIVES = 192;

    private int windowId;
    private List<Integer> targetSlots = Collections.emptyList();
    private List<List<ItemStack>> alternatives = Collections.emptyList();
    private boolean maxTransfer;
    private boolean requireCompleteSets;

    public C2SRtsJeiContainerTransferPayload() {
    }

    public C2SRtsJeiContainerTransferPayload(
            int windowId,
            List<Integer> targetSlots,
            List<List<ItemStack>> alternatives,
            boolean maxTransfer,
            boolean requireCompleteSets) {
        this.windowId = windowId;
        this.targetSlots = immutableSlots(targetSlots);
        this.alternatives = immutableAlternatives(alternatives);
        this.maxTransfer = maxTransfer;
        this.requireCompleteSets = requireCompleteSets;
    }

    public int windowId() {
        return windowId;
    }

    public List<Integer> targetSlots() {
        return targetSlots;
    }

    public List<List<ItemStack>> alternatives() {
        return alternatives;
    }

    public boolean maxTransfer() {
        return maxTransfer;
    }

    public boolean requireCompleteSets() {
        return requireCompleteSets;
    }

    public boolean isValid() {
        if (windowId <= 0 || targetSlots.isEmpty()
                || targetSlots.size() > MAX_INPUTS
                || targetSlots.size() != alternatives.size()) {
            return false;
        }
        Set<Integer> unique = new HashSet<Integer>();
        int total = 0;
        for (int i = 0; i < targetSlots.size(); i++) {
            Integer target = targetSlots.get(i);
            List<ItemStack> choices = alternatives.get(i);
            if (target == null || target < 0 || !unique.add(target)
                    || choices == null || choices.isEmpty()
                    || choices.size() > MAX_ALTERNATIVES_PER_INPUT) {
                return false;
            }
            total += choices.size();
            if (total > MAX_TOTAL_ALTERNATIVES) {
                return false;
            }
            for (ItemStack choice : choices) {
                if (choice == null || choice.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        windowId = RtsPacketBuffer.readVarInt(buffer);
        int inputCount = RtsPacketBuffer.readBoundedCount(
                buffer, MAX_INPUTS, "JEI container inputs");
        List<Integer> slots = new ArrayList<Integer>(inputCount);
        List<List<ItemStack>> decodedAlternatives =
                new ArrayList<List<ItemStack>>(inputCount);
        int total = 0;
        for (int i = 0; i < inputCount; i++) {
            slots.add(RtsPacketBuffer.readVarInt(buffer));
            int choiceCount = RtsPacketBuffer.readBoundedCount(
                    buffer, MAX_ALTERNATIVES_PER_INPUT,
                    "JEI container alternatives");
            total += choiceCount;
            if (choiceCount <= 0 || total > MAX_TOTAL_ALTERNATIVES) {
                throw new IllegalArgumentException("JEI container alternatives out of range");
            }
            List<ItemStack> choices = new ArrayList<ItemStack>(choiceCount);
            for (int choice = 0; choice < choiceCount; choice++) {
                choices.add(one(RtsPacketBuffer.readItemStack(buffer)));
            }
            decodedAlternatives.add(Collections.unmodifiableList(choices));
        }
        targetSlots = Collections.unmodifiableList(slots);
        alternatives = Collections.unmodifiableList(decodedAlternatives);
        maxTransfer = buffer.readBoolean();
        requireCompleteSets = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValid()) {
            throw new IllegalArgumentException("JEI container transfer request is invalid");
        }
        RtsPacketBuffer.writeVarInt(buffer, windowId);
        RtsPacketBuffer.writeVarInt(buffer, targetSlots.size());
        for (int i = 0; i < targetSlots.size(); i++) {
            RtsPacketBuffer.writeVarInt(buffer, targetSlots.get(i));
            List<ItemStack> choices = alternatives.get(i);
            RtsPacketBuffer.writeVarInt(buffer, choices.size());
            for (ItemStack choice : choices) {
                RtsPacketBuffer.writeItemStack(buffer, one(choice));
            }
        }
        buffer.writeBoolean(maxTransfer);
        buffer.writeBoolean(requireCompleteSets);
    }

    private static List<Integer> immutableSlots(List<Integer> source) {
        return source == null || source.isEmpty()
                ? Collections.<Integer>emptyList()
                : Collections.unmodifiableList(new ArrayList<Integer>(source));
    }

    private static List<List<ItemStack>> immutableAlternatives(
            List<List<ItemStack>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<ItemStack>> result = new ArrayList<List<ItemStack>>(source.size());
        for (List<ItemStack> choices : source) {
            List<ItemStack> copied = new ArrayList<ItemStack>();
            if (choices != null) {
                for (ItemStack choice : choices) {
                    if (choice != null && !choice.isEmpty()) {
                        copied.add(one(choice));
                    }
                }
            }
            result.add(Collections.unmodifiableList(copied));
        }
        return Collections.unmodifiableList(result);
    }

    private static ItemStack one(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
