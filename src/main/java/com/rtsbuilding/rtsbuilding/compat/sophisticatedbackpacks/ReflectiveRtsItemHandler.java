package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * 把可选模组暴露的槽位式库存对象适配为 RTS 内部物品接口。
 *
 * <p>本类只负责六个稳定的槽位操作，不负责寻找背包、UUID 生命周期或加载器能力查询。
 * 反射调用失败时始终保守返回：插入保留原物品，提取返回空，避免兼容变化导致物品丢失。
 */
final class ReflectiveRtsItemHandler implements RtsItemHandler {
    private final Object delegate;
    private final Method getSlots;
    private final Method getStackInSlot;
    private final Method insertItem;
    private final Method extractItem;
    private final Method getSlotLimit;
    private final Method isItemValid;

    private ReflectiveRtsItemHandler(Object delegate) throws NoSuchMethodException {
        Class<?> type = delegate.getClass();
        this.delegate = delegate;
        this.getSlots = type.getMethod("getSlots");
        this.getStackInSlot = type.getMethod("getStackInSlot", int.class);
        this.insertItem = type.getMethod("insertItem", int.class, ItemStack.class, boolean.class);
        this.extractItem = type.getMethod("extractItem", int.class, int.class, boolean.class);
        this.getSlotLimit = type.getMethod("getSlotLimit", int.class);
        this.isItemValid = type.getMethod("isItemValid", int.class, ItemStack.class);
    }

    static Optional<RtsItemHandler> tryWrap(Object candidate) {
        if (candidate instanceof RtsItemHandler handler) {
            return Optional.of(handler);
        }
        if (candidate == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ReflectiveRtsItemHandler(candidate));
        } catch (NoSuchMethodException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public int getSlots() {
        Object result = invoke(getSlots);
        return result instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        Object result = invoke(getStackInSlot, slot);
        return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Object result = invoke(insertItem, slot, stack, simulate);
        return result instanceof ItemStack remainder ? remainder : stack.copy();
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        Object result = invoke(extractItem, slot, amount, simulate);
        return result instanceof ItemStack extracted ? extracted : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        Object result = invoke(getSlotLimit, slot);
        return result instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Object result = invoke(isItemValid, slot, stack);
        return result instanceof Boolean valid && valid;
    }

    private Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(delegate, arguments);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }
}
