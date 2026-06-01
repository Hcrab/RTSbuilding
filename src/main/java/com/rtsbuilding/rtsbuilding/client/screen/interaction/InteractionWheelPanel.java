package com.rtsbuilding.rtsbuilding.client.screen.interaction;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

/**
 * 交互轮盘面板。
 * <p>
 * 独立的交互轮盘面板组件，处理轮盘状态的渲染、输入和状态管理。
 * 由 {@link BuilderScreen} 统一调度生命周期。
 */
public final class InteractionWheelPanel {

    private boolean open = false;
    private final List<InteractionOption> options = new ArrayList<>();
    private InteractionTarget target;
    private int page = 0;
    private int centerX = 0;
    private int centerY = 0;

    private BuilderScreen screen;
    private ClientRtsController controller;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ── 公开查询方法 ──

    public boolean isOpen() {
        return this.open;
    }

    public boolean open(double mouseX, double mouseY) {
        InteractionTarget t = screen.pickInteractionTarget(false);
        if (t == null) {
            return false;
        }
        List<InteractionOption> opts = buildInteractionOptions();
        if (opts.isEmpty()) {
            return false;
        }
        this.open = true;
        this.target = t;
        this.options.clear();
        this.options.addAll(opts);
        this.page = 0;

        int minX = INTERACT_WHEEL_RADIUS + INTERACT_WHEEL_SLOT;
        int maxX = Math.max(minX, screen.width - INTERACT_WHEEL_RADIUS - INTERACT_WHEEL_SLOT);
        int minY = TOP_H + INTERACT_WHEEL_RADIUS + INTERACT_WHEEL_SLOT;
        int maxY = Math.max(minY, screen.getBottomY() - INTERACT_WHEEL_RADIUS - INTERACT_WHEEL_SLOT);
        this.centerX = Mth.clamp((int) Math.round(mouseX), minX, maxX);
        this.centerY = Mth.clamp((int) Math.round(mouseY), minY, maxY);
        return true;
    }

    public void close() {
        this.open = false;
        this.target = null;
        this.options.clear();
        this.page = 0;
    }

    public InteractionTarget getTarget() {
        return this.target;
    }

    // ── 输入方法 ──

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            InteractionOption picked = resolveOption(mouseX, mouseY);
            if (picked != null) {
                runOption(picked);
            }
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            close();
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double scrollY) {
        if (!this.open) {
            return false;
        }
        int pageCount = getPageCount();
        if (pageCount > 1) {
            if (scrollY > 0.0D) {
                this.page = (this.page + pageCount - 1) % pageCount;
            } else if (scrollY < 0.0D) {
                this.page = (this.page + 1) % pageCount;
            }
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!this.open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        int pageCount = getPageCount();
        if (pageCount > 1 && (keyCode == GLFW.GLFW_KEY_Q || keyCode == GLFW.GLFW_KEY_LEFT)) {
            this.page = (this.page + pageCount - 1) % pageCount;
            return true;
        }
        if (pageCount > 1 && (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            this.page = (this.page + 1) % pageCount;
            return true;
        }
        return true;
    }

    // ── 渲染 ──

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        g.fill(0, TOP_H, screen.width, screen.getBottomY(), 0x66000000);

        int ringR = INTERACT_WHEEL_RADIUS + 16;
        g.fill(
                this.centerX - ringR,
                this.centerY - ringR,
                this.centerX + ringR,
                this.centerY + ringR,
                0x77151A22);
        g.fill(
                this.centerX - 24,
                this.centerY - 24,
                this.centerX + 24,
                this.centerY + 24,
                0xAA0C1118);

        List<InteractionWheelSlot> slots = collectSlots();
        Font font = screen.font();
        for (InteractionWheelSlot slot : slots) {
            int x = slot.x();
            int y = slot.y();
            boolean hover = inside(mouseX, mouseY, x, y, INTERACT_WHEEL_SLOT, INTERACT_WHEEL_SLOT);
            g.fill(x, y, x + INTERACT_WHEEL_SLOT, y + INTERACT_WHEEL_SLOT, hover ? 0xCC335369 : 0xAA1B232E);
            g.hLine(x, x + INTERACT_WHEEL_SLOT, y, 0xFF5B7085);
            g.hLine(x, x + INTERACT_WHEEL_SLOT, y + INTERACT_WHEEL_SLOT, 0xFF0C0F13);
            g.vLine(x, y, y + INTERACT_WHEEL_SLOT, 0xFF5B7085);
            g.vLine(x + INTERACT_WHEEL_SLOT, y, y + INTERACT_WHEEL_SLOT, 0xFF0C0F13);
            g.renderItem(slot.option().preview(), x + 1, y + 1);
            if (hover) {
                g.fill(x + 1, y + 1, x + INTERACT_WHEEL_SLOT - 1, y + INTERACT_WHEEL_SLOT - 1, 0x22FFFFFF);
            }
        }

        int pageCount = getPageCount();
        String title = this.target != null && this.target.isEntityTarget()
                ? "Entity Interact"
                : "Block Interact";
        g.drawCenteredString(font, title, this.centerX, this.centerY - 10, 0xEAF5FF);
        g.drawCenteredString(
                font,
                (this.page + 1) + "/" + pageCount,
                this.centerX,
                this.centerY + 2,
                0xA9C7E8);
        g.drawCenteredString(
                font,
                "LMB: use   RMB/Esc: cancel   Wheel: page",
                this.centerX,
                this.centerY + 30,
                0xB7CDE2);

        InteractionOption hover = resolveOption(mouseX, mouseY);
        if (hover != null) {
            String sourceLabel = hover.source() == InteractionSource.TOOL_SLOT
                    ? "Tool " + (hover.toolSlot() + 1)
                    : "Pin " + (hover.pinIndex() + 1);
            g.drawCenteredString(
                    font,
                    screen.trimToWidth(sourceLabel + " - " + hover.preview().getHoverName().getString(), 260),
                    this.centerX,
                    this.centerY + 42,
                    0xFFFFFF);
        }
    }

    // ── 内部方法 ──

    private List<InteractionOption> buildInteractionOptions() {
        List<InteractionOption> result = new ArrayList<>();
        if (screen.getMinecraft() == null || screen.getMinecraft().player == null) {
            return result;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = screen.getMinecraft().player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            result.add(new InteractionOption(
                    InteractionSource.TOOL_SLOT,
                    slot,
                    -1,
                    "",
                    stack.copy()));
        }

        int pinCount = this.controller.getQuickSlotCount();
        for (int pin = 0; pin < pinCount; pin++) {
            String itemId = this.controller.getQuickSlotItemId(pin);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ItemStack preview = this.controller.getQuickSlotPreview(pin);
            if (preview.isEmpty()) {
                var id = ResourceLocation.tryParse(itemId);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
            result.add(new InteractionOption(
                    InteractionSource.PIN_ITEM,
                    -1,
                    pin,
                    itemId,
                    preview.copy()));
        }
        return result;
    }

    private void runOption(InteractionOption option) {
        if (option == null || this.target == null) {
            return;
        }
        if (option.source() == InteractionSource.TOOL_SLOT) {
            if (this.target.isEntityTarget()) {
                this.controller.interactEntityWithToolSlot(
                        this.target.entityId(),
                        this.target.hitLocation(),
                        option.toolSlot(),
                        this.target.rayOrigin(),
                        this.target.rayDir());
            } else if (this.target.blockHit() != null) {
                this.controller.interactBlockWithToolSlot(
                        this.target.blockHit(),
                        option.toolSlot(),
                        this.target.rayOrigin(),
                        this.target.rayDir());
            }
            return;
        }

        if (option.source() == InteractionSource.PIN_ITEM) {
            if (this.target.isEntityTarget()) {
                this.controller.interactEntityWithPinnedItem(
                        this.target.entityId(),
                        this.target.hitLocation(),
                        option.itemId(),
                        this.target.rayOrigin(),
                        this.target.rayDir());
            } else if (this.target.blockHit() != null) {
                this.controller.interactBlockWithPinnedItem(
                        this.target.blockHit(),
                        option.itemId(),
                        this.target.rayOrigin(),
                        this.target.rayDir());
            }
        }
    }

    private int getPageCount() {
        if (this.options.isEmpty()) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(this.options.size() / (double) INTERACT_WHEEL_PAGE_SIZE));
    }

    private List<InteractionWheelSlot> collectSlots() {
        List<InteractionWheelSlot> slots = new ArrayList<>();
        if (!this.open || this.options.isEmpty()) {
            return slots;
        }

        int pageCount = getPageCount();
        this.page = Mth.clamp(this.page, 0, pageCount - 1);
        int from = this.page * INTERACT_WHEEL_PAGE_SIZE;
        int to = Math.min(this.options.size(), from + INTERACT_WHEEL_PAGE_SIZE);
        int count = Math.max(0, to - from);
        if (count <= 0) {
            return slots;
        }

        for (int i = 0; i < count; i++) {
            double angle = (-Math.PI / 2.0D) + ((Math.PI * 2.0D) * (i / (double) count));
            int cx = this.centerX + (int) Math.round(Math.cos(angle) * INTERACT_WHEEL_RADIUS);
            int cy = this.centerY + (int) Math.round(Math.sin(angle) * INTERACT_WHEEL_RADIUS);
            slots.add(new InteractionWheelSlot(
                    this.options.get(from + i),
                    cx - INTERACT_WHEEL_SLOT_HALF,
                    cy - INTERACT_WHEEL_SLOT_HALF));
        }
        return slots;
    }

    public InteractionOption resolveOption(double mouseX, double mouseY) {
        for (InteractionWheelSlot slot : collectSlots()) {
            if (inside(mouseX, mouseY, slot.x(), slot.y(), INTERACT_WHEEL_SLOT, INTERACT_WHEEL_SLOT)) {
                return slot.option();
            }
        }
        return null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
