package com.rtsbuilding.rtsbuilding.compat.jei;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Optional;

import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

final class RtsJeiIngredientCompat {
    private RtsJeiIngredientCompat() {
    }

    static Optional<IClickableIngredient<?>> createClickableIngredient(
            IIngredientManager ingredientManager,
            ItemStack stack,
            Rect2i area,
            boolean normalize) {
        if (ingredientManager == null || stack == null || stack.isEmpty() || area == null) {
            return Optional.empty();
        }

        Object raw = tryInvokeDirectClickableIngredient(ingredientManager, stack, area, normalize);
        if (raw instanceof Optional<?> optional) {
            return optional.map(clickable -> (IClickableIngredient<?>) clickable);
        }
        return Optional.empty();
    }

    private static Object tryInvokeDirectClickableIngredient(
            IIngredientManager ingredientManager,
            ItemStack stack,
            Rect2i area,
            boolean normalize) {
        return findClickableIngredientMethod(ingredientManager.getClass(), stack.getClass())
                .map(method -> invokeClickableIngredientMethod(method, ingredientManager, stack, area, normalize))
                .orElse(Optional.empty());
    }

    private static Optional<Method> findClickableIngredientMethod(Class<?> owner, Class<?> ingredientClass) {
        return java.util.Arrays.stream(owner.getMethods())
                .filter(method -> method.getName().equals("createClickableIngredient"))
                .filter(method -> method.getReturnType() == Optional.class)
                .filter(method -> isSupportedClickableIngredientSignature(method, ingredientClass))
                .sorted(Comparator.comparingInt(Method::getParameterCount).reversed())
                .findFirst();
    }

    private static boolean isSupportedClickableIngredientSignature(Method method, Class<?> ingredientClass) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 3) {
            return params[0].isAssignableFrom(ingredientClass)
                    && Rect2i.class.isAssignableFrom(params[1])
                    && (params[2] == boolean.class || params[2] == Boolean.class);
        }
        if (params.length == 2) {
            return params[0].isAssignableFrom(ingredientClass)
                    && Rect2i.class.isAssignableFrom(params[1]);
        }
        return false;
    }

    private static Object invokeClickableIngredientMethod(
            Method method,
            IIngredientManager ingredientManager,
            ItemStack stack,
            Rect2i area,
            boolean normalize) {
        try {
            method.setAccessible(true);
            if (method.getParameterCount() == 3) {
                return method.invoke(ingredientManager, stack.copy(), area, normalize);
            }
            return method.invoke(ingredientManager, stack.copy(), area);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
