package com.rtsbuilding.rtsbuilding.compat.ftb;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * FTB Quests 1.12 的非消耗型物品任务检测。
 *
 * <p>只提升任务进度，不降低已有进度，也不处理消耗物品或“仅合成”任务。
 * 任务匹配完全交给旧版 {@code ItemTask#test(ItemStack)}，因此 metadata、NBT 与
 * Item Filters 的判定仍由 FTB Quests 自己负责。</p>
 */
final class RtsFtbCompatImpl {
    private final Field serverQuestFileInstanceField;
    private final Method serverQuestFileGetDataMethod;
    private final Field chaptersField;
    private final Field chapterQuestsField;
    private final Field questTasksField;
    private final Class<?> itemTaskClass;
    private final Method itemTaskConsumesResourcesMethod;
    private final Method itemTaskTestMethod;
    private final Method itemTaskGetMaxProgressMethod;
    private final Field itemTaskOnlyFromCraftingField;
    private final Method tristateGetMethod;
    private final Method questDataGetTaskDataMethod;
    private final Field taskDataProgressField;
    private final Method taskDataSetProgressMethod;

    RtsFtbCompatImpl() throws ReflectiveOperationException {
        Class<?> serverQuestFileClass = Class.forName("com.feed_the_beast.ftbquests.quest.ServerQuestFile");
        Class<?> questFileClass = Class.forName("com.feed_the_beast.ftbquests.quest.QuestFile");
        Class<?> chapterClass = Class.forName("com.feed_the_beast.ftbquests.quest.Chapter");
        Class<?> questClass = Class.forName("com.feed_the_beast.ftbquests.quest.Quest");
        Class<?> questDataClass = Class.forName("com.feed_the_beast.ftbquests.quest.QuestData");
        Class<?> taskClass = Class.forName("com.feed_the_beast.ftbquests.quest.task.Task");
        Class<?> taskDataClass = Class.forName("com.feed_the_beast.ftbquests.quest.task.TaskData");
        this.itemTaskClass = Class.forName("com.feed_the_beast.ftbquests.quest.task.ItemTask");

        this.serverQuestFileInstanceField = serverQuestFileClass.getField("INSTANCE");
        this.serverQuestFileGetDataMethod = serverQuestFileClass.getMethod("getData", UUID.class);
        this.chaptersField = questFileClass.getField("chapters");
        this.chapterQuestsField = chapterClass.getField("quests");
        this.questTasksField = questClass.getField("tasks");
        this.itemTaskConsumesResourcesMethod = this.itemTaskClass.getMethod("consumesResources");
        this.itemTaskTestMethod = this.itemTaskClass.getMethod("test", ItemStack.class);
        this.itemTaskGetMaxProgressMethod = this.itemTaskClass.getMethod("getMaxProgress");
        this.itemTaskOnlyFromCraftingField = this.itemTaskClass.getField("onlyFromCrafting");
        this.tristateGetMethod = this.itemTaskOnlyFromCraftingField.getType().getMethod("get", boolean.class);
        this.questDataGetTaskDataMethod = questDataClass.getMethod("getTaskData", taskClass);
        this.taskDataProgressField = taskDataClass.getField("progress");
        this.taskDataSetProgressMethod = taskDataClass.getMethod("setProgress", long.class);
    }

    RtsFtbCompat.QuestDetectResult detectNow(EntityPlayerMP player) {
        if (player == null) {
            return RtsFtbCompat.QuestDetectResult.unavailable();
        }
        try {
            Object questFile = this.serverQuestFileInstanceField.get(null);
            if (questFile == null) {
                return RtsFtbCompat.QuestDetectResult.unavailable();
            }
            Object questData = this.serverQuestFileGetDataMethod.invoke(questFile, player.getUniqueID());
            if (questData == null) {
                return RtsFtbCompat.QuestDetectResult.complete(0, 0);
            }

            int scannedTasks = 0;
            int newlyCompletedTasks = 0;
            for (Object task : allTasks(questFile)) {
                if (task == null || !this.itemTaskClass.isInstance(task)
                        || asBoolean(this.itemTaskConsumesResourcesMethod.invoke(task))
                        || isOnlyFromCrafting(task)) {
                    continue;
                }

                scannedTasks++;
                long total = countInPlayerInventory(task, player)
                        + ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(
                                player, stack -> testItemTask(task, stack));
                long maxProgress = asLong(this.itemTaskGetMaxProgressMethod.invoke(task));
                long clamped = Math.max(0L, Math.min(total, maxProgress));
                Object taskData = this.questDataGetTaskDataMethod.invoke(questData, task);
                if (taskData == null) {
                    continue;
                }
                long previousProgress = asLong(this.taskDataProgressField.get(taskData));
                if (clamped > previousProgress) {
                    this.taskDataSetProgressMethod.invoke(taskData, clamped);
                }
                if (maxProgress > 0L && previousProgress < maxProgress && clamped >= maxProgress) {
                    newlyCompletedTasks++;
                }
            }
            return RtsFtbCompat.QuestDetectResult.complete(scannedTasks, newlyCompletedTasks);
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn(
                    "FTB Quests 1.12 detect failed for player {}.", player.getName(), throwable);
            return RtsFtbCompat.QuestDetectResult.failed();
        }
    }

    private List<Object> allTasks(Object questFile) throws ReflectiveOperationException {
        java.util.ArrayList<Object> tasks = new java.util.ArrayList<>();
        for (Object chapter : asCollection(this.chaptersField.get(questFile))) {
            for (Object quest : asCollection(this.chapterQuestsField.get(chapter))) {
                tasks.addAll(asCollection(this.questTasksField.get(quest)));
            }
        }
        return tasks;
    }

    private boolean isOnlyFromCrafting(Object task) throws ReflectiveOperationException {
        Object tristate = this.itemTaskOnlyFromCraftingField.get(task);
        return tristate != null && asBoolean(this.tristateGetMethod.invoke(tristate, false));
    }

    private long countInPlayerInventory(Object itemTask, EntityPlayerMP player) {
        long total = 0L;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && testItemTask(itemTask, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean testItemTask(Object itemTask, ItemStack stack) {
        try {
            return asBoolean(this.itemTaskTestMethod.invoke(itemTask, stack));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> asCollection(Object value) {
        return value instanceof Collection ? (Collection<Object>) value : Collections.emptyList();
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
