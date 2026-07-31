package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

public final class DarkUiPalette {

    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/dark.png");

    private static int bg;
    private static int accent;
    private static int border;
    private static int black;
    private static int hoverBorder;
    private static int toggleOn;
    private static int p6;
    private static int p7;

    private static boolean loaded;

    private DarkUiPalette() {}

    public static void load() {
        if (loaded) return;
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(TEXTURE).orElse(null);
            if (resource == null) return;
            try (var stream = resource.open()) {
                var image = NativeImage.read(stream);
                bg = abgrToArgb(image.getPixelRGBA(0, 0));
                accent = abgrToArgb(image.getPixelRGBA(1, 0));
                border = abgrToArgb(image.getPixelRGBA(2, 0));
                black = abgrToArgb(image.getPixelRGBA(3, 0));
                hoverBorder = image.getWidth() > 4 ? abgrToArgb(image.getPixelRGBA(4, 0)) : black;
                toggleOn = image.getWidth() > 5 ? abgrToArgb(image.getPixelRGBA(5, 0)) : hoverBorder;
                p6 = image.getWidth() > 6 ? abgrToArgb(image.getPixelRGBA(6, 0)) : toggleOn;
                p7 = image.getWidth() > 7 ? abgrToArgb(image.getPixelRGBA(7, 0)) : p6;
                image.close();
            }
        } catch (IOException e) {
            return;
        }
        loaded = true;
    }

    public static int bg() { load(); return bg; }
    public static int accent() { load(); return accent; }
    public static int border() { load(); return border; }
    public static int black() { load(); return black; }
    public static int hoverBorder() { load(); return hoverBorder; }
    public static int toggleOn() { load(); return toggleOn; }
    public static int p6() { load(); return p6; }
    public static int p7() { load(); return p7; }

    public static boolean isLoaded() { return loaded; }

    private static int abgrToArgb(int abgr) {
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
