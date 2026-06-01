package com.rtsbuilding.rtsbuilding.compat.ftb;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RtsFtbCompatImpl {
    private final Method teamsApiMethod;
    private final Method getTeamManagerMethod;
    private final Method getTeamForPlayerMethod;
    private final boolean teamLookupUsesServerPlayer;
    private final Class<?> serverQuestFileClass;
    private final Method serverQuestFileExistsMethod;
    private final Method serverQuestFileGetInstanceMethod;
    private final Field serverQuestFileInstanceField;
    private final Method getOrCreateTeamDataMethod;
    private final boolean teamDataMethodUsesUuid;
    private final Method teamGetIdMethod;
    private final Method getSubmitTasksMethod;
    private final Method collectTasksMethod;
    private final Field submitTasksField;
    private final Class<?> itemTaskClass;
    private final Method itemTaskConsumesResourcesMethod;
    private final Method itemTaskOnlyFromCraftingMethod;
    private final Method itemTaskTestMethod;
    private final Method itemTaskGetMaxProgressMethod;
    private final Map<Class<?>, Method> teamDataSetProgressMethodCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> teamDataGetProgressMethodCache = new ConcurrentHashMap<>();

    RtsFtbCompatImpl() throws ReflectiveOperationException {
        Class<?> ftbTeamsApiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        Class<?> ftbTeamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
        this.serverQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
        this.itemTaskClass = Class.forName("dev.ftb.mods.ftbquests.quest.task.ItemTask");

        this.teamsApiMethod = ftbTeamsApiClass.getMethod("api");
        this.getTeamManagerMethod = this.teamsApiMethod.getReturnType().getMethod("getManager");
        this.getTeamForPlayerMethod = resolveTeamLookupMethod(this.getTeamManagerMethod.getReturnType());
        this.teamLookupUsesServerPlayer = this.getTeamForPlayerMethod.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class);
        this.teamGetIdMethod = findPublicNoArgMethod(ftbTeamClass, "getId", "getTeamId");

        this.serverQuestFileExistsMethod = findOptionalMethod(this.serverQuestFileClass, "exists");
        this.serverQuestFileGetInstanceMethod = findOptionalMethod(this.serverQuestFileClass, "getInstance");
        this.serverQuestFileInstanceField = findOptionalField(this.serverQuestFileClass, "INSTANCE");
        if (this.serverQuestFileGetInstanceMethod == null && this.serverQuestFileInstanceField == null) {
            throw new NoSuchFieldException("Missing ServerQuestFile instance accessor");
        }
        this.getOrCreateTeamDataMethod = resolveGetOrCreateTeamDataMethod(this.serverQuestFileClass, ftbTeamClass);
        this.teamDataMethodUsesUuid = this.getOrCreateTeamDataMethod.getParameterTypes()[0] == UUID.class;
        this.getSubmitTasksMethod = findOptionalMethod(this.serverQuestFileClass, "getSubmitTasks");
        this.collectTasksMethod = findOptionalMethod(this.serverQuestFileClass, "collect", Class.class);
        this.submitTasksField = findOptionalField(this.serverQuestFileClass, "submitTasks");

        this.itemTaskConsumesResourcesMethod = this.itemTaskClass.getMethod("consumesResources");
        this.itemTaskOnlyFromCraftingMethod = findPublicNoArgMethod(this.itemTaskClass, "isOnlyFromCrafting", "onlyFromCrafting");
        this.itemTaskTestMethod = this.itemTaskClass.getMethod("test", ItemStack.class);
        this.itemTaskGetMaxProgressMethod = this.itemTaskClass.getMethod("getMaxProgress");
    }

    RtsFtbCompat.QuestDetectResult detectNow(ServerPlayer player) {
        if (player == null) {
            return RtsFtbCompat.QuestDetectResult.unavailable();
        }
        try {
            Object serverQuestFile = resolveServerQuestFileInstance();
            if (serverQuestFile == null) {
                return RtsFtbCompat.QuestDetectResult.unavailable();
            }

            Object team = resolveTeam(player);
            if (team == null) {
                return RtsFtbCompat.QuestDetectResult.complete(0, 0);
            }

            Object teamData = resolveTeamData(serverQuestFile, team);
            if (teamData == null) {
                return RtsFtbCompat.QuestDetectResult.complete(0, 0);
            }

            Collection<?> submitTasks = readSubmitTasks(serverQuestFile);
            if (submitTasks == null || submitTasks.isEmpty()) {
                return RtsFtbCompat.QuestDetectResult.complete(0, 0);
            }

            Method setProgressMethod = resolveSetProgressMethod(teamData.getClass());
            int scannedTasks = 0;
            int newlyCompletedTasks = 0;
            for (Object task : submitTasks) {
                if (task == null || !this.itemTaskClass.isInstance(task)) {
                    continue;
                }

                boolean consumesResources = asBoolean(this.itemTaskConsumesResourcesMethod.invoke(task));
                if (consumesResources) {
                    continue;
                }
                boolean onlyFromCrafting = asBoolean(this.itemTaskOnlyFromCraftingMethod.invoke(task));
                if (onlyFromCrafting) {
                    continue;
                }

                scannedTasks++;
                long total = countInPlayerInventory(task, player)
                        + RtsStorageManager.countLinkedItemsMatching(player, stack -> testItemTask(task, stack));
                long maxProgress = asLong(this.itemTaskGetMaxProgressMethod.invoke(task));
                long clamped = Math.max(0L, Math.min(total, maxProgress));
                long previousProgress = readProgress(teamData, task);
                if (clamped > previousProgress) {
                    setProgressMethod.invoke(teamData, task, clamped);
                }
                if (maxProgress > 0L && previousProgress < maxProgress && clamped >= maxProgress) {
                    newlyCompletedTasks++;
                }
            }
            return RtsFtbCompat.QuestDetectResult.complete(scannedTasks, newlyCompletedTasks);
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn("FTB quest detect run failed for player {}", player.getScoreboardName(), throwable);
            return RtsFtbCompat.QuestDetectResult.failed();
        }
    }

    private Object resolveServerQuestFileInstance() throws ReflectiveOperationException {
        if (this.serverQuestFileExistsMethod != null && !asBoolean(this.serverQuestFileExistsMethod.invoke(null))) {
            return null;
        }
        if (this.serverQuestFileGetInstanceMethod != null) {
            try {
                return this.serverQuestFileGetInstanceMethod.invoke(null);
            } catch (InvocationTargetException invocationTargetException) {
                if (invocationTargetException.getCause() instanceof NullPointerException) {
                    return null;
                }
                throw invocationTargetException;
            }
        }
        return this.serverQuestFileInstanceField.get(null);
    }

    private Object resolveTeam(ServerPlayer player) throws ReflectiveOperationException {
        Object api = this.teamsApiMethod.invoke(null);
        if (api == null) {
            return null;
        }
        Object manager = this.getTeamManagerMethod.invoke(api);
        if (manager == null) {
            return null;
        }
        Object rawTeam = this.teamLookupUsesServerPlayer
                ? this.getTeamForPlayerMethod.invoke(manager, player)
                : this.getTeamForPlayerMethod.invoke(manager, player.getUUID());
        return unwrapOptional(rawTeam);
    }

    private Object resolveTeamData(Object serverQuestFile, Object team) throws ReflectiveOperationException {
        if (this.teamDataMethodUsesUuid) {
            Object teamId = this.teamGetIdMethod.invoke(team);
            if (!(teamId instanceof UUID)) {
                return null;
            }
            return this.getOrCreateTeamDataMethod.invoke(serverQuestFile, teamId);
        }
        return this.getOrCreateTeamDataMethod.invoke(serverQuestFile, team);
    }

    private Collection<?> readSubmitTasks(Object serverQuestFile) throws ReflectiveOperationException {
        if (this.getSubmitTasksMethod != null) {
            Object submitTasks = this.getSubmitTasksMethod.invoke(serverQuestFile);
            if (submitTasks instanceof Collection<?> collection) {
                return collection;
            }
        }
        if (this.collectTasksMethod != null) {
            Object collected = this.collectTasksMethod.invoke(serverQuestFile, this.itemTaskClass);
            if (collected instanceof Collection<?> collection) {
                return collection;
            }
        }
        if (this.submitTasksField == null) {
            return java.util.List.of();
        }
        Object raw = this.submitTasksField.get(serverQuestFile);
        if (raw instanceof Collection<?> collection) {
            return collection;
        }
        return null;
    }

    private long readProgress(Object teamData, Object task) throws ReflectiveOperationException {
        Method method = resolveGetProgressMethod(teamData.getClass());
        return asLong(method.invoke(teamData, task));
    }

    private Method resolveSetProgressMethod(Class<?> teamDataClass) {
        return this.teamDataSetProgressMethodCache.computeIfAbsent(teamDataClass, cls -> {
            for (Method method : cls.getMethods()) {
                if (!"setProgress".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (!params[0].isAssignableFrom(this.itemTaskClass)) {
                    continue;
                }
                if (params[1] != long.class && params[1] != Long.class) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            throw new IllegalStateException("Missing TeamData.setProgress(task,long) method");
        });
    }

    private Method resolveGetProgressMethod(Class<?> teamDataClass) {
        return this.teamDataGetProgressMethodCache.computeIfAbsent(teamDataClass, cls -> {
            for (Method method : cls.getMethods()) {
                if (!"getProgress".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (!parameterType.isAssignableFrom(this.itemTaskClass)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            throw new IllegalStateException("Missing TeamData.getProgress(task) method");
        });
    }

    private long countInPlayerInventory(Object itemTask, ServerPlayer player) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }
            if (testItemTask(itemTask, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean testItemTask(Object itemTask, ItemStack stack) {
        try {
            return asBoolean(this.itemTaskTestMethod.invoke(itemTask, stack));
        } catch (ReflectiveOperationException reflectiveOperationException) {
            return false;
        }
    }

    private static Method findMethodByNameAndArity(Class<?> type, String name, int arity) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != arity) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("Missing method: " + type.getName() + "#" + name + "/" + arity);
    }

    private static Method resolveGetOrCreateTeamDataMethod(Class<?> serverQuestFileClass, Class<?> ftbTeamClass) {
        Method teamMethod = null;
        Method uuidMethod = null;
        Class<?> current = serverQuestFileClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"getOrCreateTeamData".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType == UUID.class) {
                    uuidMethod = method;
                } else if (parameterType.isAssignableFrom(ftbTeamClass)) {
                    teamMethod = method;
                }
            }
            current = current.getSuperclass();
        }
        Method method = teamMethod != null ? teamMethod : uuidMethod;
        if (method == null) {
            throw new IllegalStateException("Missing ServerQuestFile#getOrCreateTeamData");
        }
        method.setAccessible(true);
        return method;
    }

    private static Method findOptionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findFieldByName(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Missing field: " + type.getName() + "#" + name);
    }

    private static Field findOptionalField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method resolveTeamLookupMethod(Class<?> managerClass) throws NoSuchMethodException {
        for (String name : new String[] { "getTeamForPlayerID", "getTeamForPlayer" }) {
            for (Method method : managerClass.getMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType == UUID.class || parameterType.isAssignableFrom(ServerPlayer.class)) {
                    return method;
                }
            }
        }
        throw new NoSuchMethodException("Missing team lookup method on " + managerClass.getName());
    }

    private static Method findPublicNoArgMethod(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try next candidate.
            }
        }
        throw new NoSuchMethodException("Missing no-arg method on " + type.getName());
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
