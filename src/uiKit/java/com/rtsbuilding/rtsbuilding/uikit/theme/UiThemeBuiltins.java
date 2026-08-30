package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.EnumMap;
import java.util.Map;

/** 五套正式内置主题及其完整核心令牌表。 */
public final class UiThemeBuiltins {
    public static final String LEGACY_ID = "rtsbuilding:legacy";
    public static final String CALIBRATED_ID = "rtsbuilding:calibrated_dark";
    public static final String NORD_ID = "rtsbuilding:nord_command";
    public static final String CARBON_ID = "rtsbuilding:carbon_operations";
    public static final String MATERIAL_ID = "rtsbuilding:material_field";
    public static final String PIXEL_TEXTURE_SET = "rtsbuilding:pixel_default";

    public static UiThemeRegistry createRegistry() {
        UiThemeRegistry registry = new UiThemeRegistry();
        registry.register(legacy());
        registry.register(calibratedDark());
        registry.register(nordCommand());
        registry.register(carbonOperations());
        registry.register(materialField());
        return registry;
    }

    public static UiThemeDefinition legacy() {
        Seed seed = new Seed(
                0xFF101116, 0xC0101116, 0xD014151A,
                0xFF161C24, 0xFF233345, 0xFF11161C,
                0xFF6C839A, 0xFF536679, 0xFF2A3A4A, 0xFFAAC8E8,
                0xFFF2F7FF, 0xFFD8E2EE, 0xFF9FB0C2, 0xFFFFFFFF,
                0xAA1F2329, 0xFF1D2530, 0xFF1F5037, 0xFF2D6B47, 0x880B0E12,
                0xFF72F07A,
                0xFFBDC9D6, 0xFF9FB0C2, 0xFFF4FBF5,
                0xFF7CCB93, 0xFF355B4C,
                0xFFB8FFB8, 0xFFFFC96B, 0xFFE36B6B, 0xFFB04444,
                0xFF324153, 0xFF3E5268, 0xFF2D6B47, 0xFFE36B6B,
                0xFF1A202A, 0xFF536679, 0xFF7A90AA,
                0xFF7CCB93, 0x4A7CCB93, 0xFF62C8FF, 0xFFE36B6B,
                0x667CCB93, 0x66E36B6B,
                0xFFFF5752, 0xFF5CFF6B, 0xFF61A3FF, 0xFFFFC72E);
        return definition(LEGACY_ID, "screen.rtsbuilding.theme.legacy", "RTS Building",
                "screen.rtsbuilding.theme.legacy.description", UiThemeRenderMode.LEGACY_DIRECT,
                "rtsbuilding:legacy_direct", false, seed);
    }

    public static UiThemeDefinition calibratedDark() {
        Seed seed = new Seed(
                0xFF0F1318, 0xE6131820, 0xE611161D,
                0xFF171F28, 0xFF1E2734, 0xFF12171E,
                0xFF3A4B5C, 0xFF232E3A, 0xFF2B3744, 0xFF4EE2A0,
                0xFFECF2F9, 0xFF9DAEC2, 0xFF6C7F96, 0xFF0F1318,
                0xFF1A232E, 0xFF243142, 0xFF141C25, 0xFF1B3E33, 0x8812171E,
                0xFF4EE2A0,
                0xFFD3DFEE, 0xFF6C7F96, 0xFFFFFFFF,
                0xFF34AD78, 0xFF25855C,
                0xFF36B37E, 0xFFE5983B, 0xFFD9534F, 0xFFA83232,
                0xFF151D26, 0xFF2A3848, 0xFF1B3E33, 0xFFD9534F,
                0xFF121820, 0xFF2E3D4F, 0xFF435970,
                0xFF2AE89E, 0x4A2AE89E, 0xFF38C0FF, 0xFFD9534F,
                0x662AE89E, 0x66D9534F,
                0xFFFF6B66, 0xFF55D98A, 0xFF58A6FF, 0xFFFFC857);
        return palette(CALIBRATED_ID, "calibrated", seed);
    }

    public static UiThemeDefinition nordCommand() {
        Seed seed = new Seed(
                0xFF2E3440, 0xE62E3440, 0xE63B4252,
                0xFF3B4252, 0xFF434C5E, 0xFF2E3440,
                0xFF4C566A, 0xFF434C5E, 0xFF4C566A, 0xFF8FBCBB,
                0xFFECEFF4, 0xFFD8DEE9, 0xFF9AA7B8, 0xFF2E3440,
                0xFF3B4252, 0xFF4C566A, 0xFF2E3440, 0xFF405968, 0x88434C5E,
                0xFF88C0D0,
                0xFFE5E9F0, 0xFF9AA7B8, 0xFF2E3440,
                0xFF88C0D0, 0xFF5E81AC,
                0xFFA3BE8C, 0xFFEBCB8B, 0xFFBF616A, 0xFFBF616A,
                0xFF343B49, 0xFF4C566A, 0xFF405968, 0xFFBF616A,
                0xFF2E3440, 0xFF5E6A7D, 0xFF81A1C1,
                0xFF88C0D0, 0x4A88C0D0, 0xFF81A1C1, 0xFFBF616A,
                0x6688C0D0, 0x66BF616A,
                0xFFBF616A, 0xFFA3BE8C, 0xFF81A1C1, 0xFFEBCB8B);
        return palette(NORD_ID, "nord", seed);
    }

    public static UiThemeDefinition carbonOperations() {
        Seed seed = new Seed(
                0xFF161616, 0xED161616, 0xED262626,
                0xFF262626, 0xFF393939, 0xFF161616,
                0xFF6F6F6F, 0xFF393939, 0xFF525252, 0xFF78A9FF,
                0xFFF4F4F4, 0xFFC6C6C6, 0xFF8D8D8D, 0xFFFFFFFF,
                0xFF262626, 0xFF353535, 0xFF161616, 0xFF163B78, 0x88393939,
                0xFF78A9FF,
                0xFFF4F4F4, 0xFF8D8D8D, 0xFFFFFFFF,
                0xFF0F62FE, 0xFF4589FF,
                0xFF42BE65, 0xFFF1C21B, 0xFFFA4D56, 0xFFDA1E28,
                0xFF1F1F1F, 0xFF393939, 0xFF163B78, 0xFFFA4D56,
                0xFF161616, 0xFF525252, 0xFF6F6F6F,
                0xFF4589FF, 0x4A4589FF, 0xFF33B1FF, 0xFFFA4D56,
                0x6642BE65, 0x66FA4D56,
                0xFFFA4D56, 0xFF42BE65, 0xFF4589FF, 0xFFF1C21B);
        return palette(CARBON_ID, "carbon", seed);
    }

    public static UiThemeDefinition materialField() {
        Seed seed = new Seed(
                0xFF111411, 0xE6111411, 0xE6191C19,
                0xFF191C19, 0xFF20251F, 0xFF111411,
                0xFF414940, 0xFF2C332B, 0xFF353C34, 0xFFD0F49A,
                0xFFE1E3DD, 0xFFC2C9BE, 0xFF899187, 0xFF17210F,
                0xFF20251F, 0xFF2B322A, 0xFF151914, 0xFF334725, 0x8820251F,
                0xFFD0F49A,
                0xFFDDE8D5, 0xFF899187, 0xFF17210F,
                0xFFACD370, 0xFF759B43,
                0xFF81C784, 0xFFFFB95C, 0xFFFFB4AB, 0xFFBA1A1A,
                0xFF171B16, 0xFF2B322A, 0xFF334725, 0xFFFFB4AB,
                0xFF111411, 0xFF414940, 0xFF586356,
                0xFFACD370, 0x4AACD370, 0xFF75D4FF, 0xFFFFB4AB,
                0x66ACD370, 0x66FFB4AB,
                0xFFFFB4AB, 0xFF81C784, 0xFF75D4FF, 0xFFFFB95C);
        return palette(MATERIAL_ID, "material", seed);
    }

    private static UiThemeDefinition palette(String id, String key, Seed seed) {
        return definition(id, "screen.rtsbuilding.theme." + key, "RTS Building",
                "screen.rtsbuilding.theme." + key + ".description", UiThemeRenderMode.PALETTE,
                PIXEL_TEXTURE_SET, true, seed);
    }

    private static UiThemeDefinition definition(String id, String nameKey, String author,
                                                String descriptionKey, UiThemeRenderMode mode,
                                                String textureSet, boolean editable, Seed seed) {
        return new UiThemeDefinition(id, nameKey, author, descriptionKey, mode, textureSet,
                editable, seed.toTokens());
    }

    /** 这里保留逐角色种子，避免相近但职责不同的颜色在 JSON 导出时被隐藏为隐式默认值。 */
    private static final class Seed {
        private final int[] values;

        Seed(int... values) {
            if (values.length != UiThemeToken.values().length) {
                throw new IllegalArgumentException("theme seed expected "
                        + UiThemeToken.values().length + " colors, got " + values.length);
            }
            this.values = values;
        }

        Map<UiThemeToken, UiColor> toTokens() {
            EnumMap<UiThemeToken, UiColor> tokens =
                    new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
            UiThemeToken[] catalog = UiThemeToken.values();
            for (int i = 0; i < catalog.length; i++) {
                tokens.put(catalog[i], new UiColor(values[i]));
            }
            return tokens;
        }
    }

    private UiThemeBuiltins() {
    }
}
