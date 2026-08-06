package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 五套亮色主题研究样本；只供无头预览和配色评审使用，不进入正式内建主题注册表。
 *
 * <p>这里负责把外部成熟色板映射到完整 RTS 语义令牌，但不负责保存玩家配置、切换正式主题，
 * 也不承诺这些研究样本会成为发布版预设。这样可以先用真实界面验证配色，再由维护者选择值得产品化的方案。</p>
 */
public final class UiThemeLightStudies {
    public static final String CLAUDE_ID = "research:claude_warm";
    public static final String CARBON_MIST_ID = "research:carbon_blue_mist";
    public static final String RADIX_IRIS_ID = "research:radix_iris_slate";
    public static final String CATPPUCCIN_ID = "research:catppuccin_latte";
    public static final String ROSE_PINE_ID = "research:rose_pine_dawn";

    public static List<UiThemeDefinition> all() {
        List<UiThemeDefinition> studies = new ArrayList<UiThemeDefinition>();
        studies.add(claudeWarm());
        studies.add(carbonBlueMist());
        studies.add(radixIrisSlate());
        studies.add(catppuccinLatte());
        studies.add(rosePineDawn());
        return Collections.unmodifiableList(studies);
    }

    public static UiThemeDefinition claudeWarm() {
        return definition(CLAUDE_ID, "Claude Warm Editorial", "Anthropic / RTS adaptation", new Seed(
                0xFFF5F4ED, 0xFFFAF9F5, 0xFFF5F4ED,
                0xFFFAF9F5, 0xFFFFFFFF, 0xFFF5F4ED,
                0xFF85847F, 0xFFD9D7CF, 0xFFE8E6DC, 0xFF3266AD,
                0xFF141413, 0xFF3D3D3A, 0xFF73726C, 0xFFFFFFFF,
                0xFFF5F4ED, 0xFFFFFFFF, 0xFFE8E6DC, 0xFFD97757, 0xFFE8E6DC,
                0xFFD97757,
                0xFF141413, 0xFF73726C, 0xFFFFFFFF,
                0xFFD97757, 0xFF6A9BCC,
                0xFF265B19, 0xFF5A4815, 0xFF7F2C28, 0xFF7F2C28,
                0xFFFFFFFF, 0xFFF5F4ED, 0xFFF6EEDF, 0xFFF7ECEC,
                0xFFE8E6DC, 0xFF85847F, 0xFF6A9BCC,
                0xFFD97757, 0x4AD97757, 0xFF3266AD, 0xFF7F2C28,
                0x66D97757, 0x667F2C28,
                0xFFB24B43, 0xFF43803A, 0xFF3266AD, 0xFFB88718));
    }

    public static UiThemeDefinition carbonBlueMist() {
        return definition(CARBON_MIST_ID, "Carbon Blue Mist", "IBM Carbon / RTS adaptation", new Seed(
                0xFFEDF5FF, 0xFFD0E2FF, 0xFFEDF5FF,
                0xFFEDF5FF, 0xFFFFFFFF, 0xFFD0E2FF,
                0xFF5C6F82, 0xFFA6C8FF, 0xFFDDE1E6, 0xFF0F62FE,
                0xFF161616, 0xFF393939, 0xFF525252, 0xFFFFFFFF,
                0xFFD0E2FF, 0xFFA6C8FF, 0xFF8AB6F9, 0xFF0F62FE, 0xFFF2F4F8,
                0xFF0F62FE,
                0xFF161616, 0xFF525252, 0xFFFFFFFF,
                0xFF0F62FE, 0xFF4589FF,
                0xFF198038, 0xFF8E6A00, 0xFFDA1E28, 0xFFB81922,
                0xFFFFFFFF, 0xFFEDF5FF, 0xFFD0E2FF, 0xFFFFE0E0,
                0xFFD0E2FF, 0xFFA6C8FF, 0xFF4589FF,
                0xFF0F62FE, 0x4A0F62FE, 0xFF009D9A, 0xFFDA1E28,
                0x660F62FE, 0x66DA1E28,
                0xFFDA1E28, 0xFF198038, 0xFF0F62FE, 0xFFF1C21B));
    }

    public static UiThemeDefinition radixIrisSlate() {
        return definition(RADIX_IRIS_ID, "Radix Iris + Slate", "Radix Colors / RTS adaptation", new Seed(
                0xFFF9F9FB, 0xFFF0F0F3, 0xFFF9F9FB,
                0xFFF0F0F3, 0xFFFCFCFD, 0xFFE8E8EC,
                0xFF80838D, 0xFFCDCED6, 0xFFE0E1E6, 0xFF5B5BD6,
                0xFF1C2024, 0xFF424750, 0xFF60646C, 0xFFFFFFFF,
                0xFFE8E8EC, 0xFFE0E1E6, 0xFFD9D9E0, 0xFF5B5BD6, 0xFFF0F0F3,
                0xFF5B5BD6,
                0xFF1C2024, 0xFF60646C, 0xFFFFFFFF,
                0xFF5B5BD6, 0xFF0D74CE,
                0xFF218358, 0xFF8A5300, 0xFFCE2C31, 0xFFB51D24,
                0xFFFCFCFD, 0xFFF0F0F3, 0xFFE6E7FF, 0xFFFFE5E5,
                0xFFE8E8EC, 0xFF8B8D98, 0xFF5B5BD6,
                0xFF5B5BD6, 0x4A5B5BD6, 0xFF0D74CE, 0xFFCE2C31,
                0x665B5BD6, 0x66CE2C31,
                0xFFE5484D, 0xFF30A46C, 0xFF0090FF, 0xFFFFC53D));
    }

    public static UiThemeDefinition catppuccinLatte() {
        return definition(CATPPUCCIN_ID, "Catppuccin Latte", "Catppuccin / RTS adaptation", new Seed(
                0xFFEFF1F5, 0xFFE6E9EF, 0xFFDCE0E8,
                0xFFE6E9EF, 0xFFEFF1F5, 0xFFDCE0E8,
                0xFF7C7F93, 0xFFBCC0CC, 0xFFCCD0DA, 0xFF1E66F5,
                0xFF4C4F69, 0xFF5C5F77, 0xFF6C6F85, 0xFFFFFFFF,
                0xFFCCD0DA, 0xFFBCC0CC, 0xFFACB0BE, 0xFF1E66F5, 0xFFE6E9EF,
                0xFF1E66F5,
                0xFF4C4F69, 0xFF6C6F85, 0xFFFFFFFF,
                0xFF1E66F5, 0xFF8839EF,
                0xFF338522, 0xFFA86600, 0xFFD20F39, 0xFFB70D32,
                0xFFEFF1F5, 0xFFE6E9EF, 0xFFCCD0DA, 0xFFF8DDE2,
                0xFFDCE0E8, 0xFF9CA0B0, 0xFF7287FD,
                0xFF1E66F5, 0x4A1E66F5, 0xFF179299, 0xFFD20F39,
                0x661E66F5, 0x66D20F39,
                0xFFD20F39, 0xFF40A02B, 0xFF1E66F5, 0xFFDF8E1D));
    }

    public static UiThemeDefinition rosePineDawn() {
        return definition(ROSE_PINE_ID, "Rosé Pine Dawn Cozy", "Rosé Pine / RTS adaptation", new Seed(
                0xFFFAF4ED, 0xFFF2E9E1, 0xFFF4EDE8,
                0xFFFAF4ED, 0xFFFFFAF3, 0xFFF2E9E1,
                0xFF797593, 0xFFCECACD, 0xFFDFDAD9, 0xFF286983,
                0xFF575279, 0xFF696482, 0xFF797593, 0xFFFFFFFF,
                0xFFF4EDE8, 0xFFFFFAF3, 0xFFDFDAD9, 0xFFB4637A, 0xFFF2E9E1,
                0xFFB4637A,
                0xFF575279, 0xFF797593, 0xFFFFFFFF,
                0xFFB4637A, 0xFFEA9D34,
                0xFF286983, 0xFF9A5E00, 0xFFB4637A, 0xFF963D5A,
                0xFFFFFAF3, 0xFFF2E9E1, 0xFFF5DFE5, 0xFFF2DDE3,
                0xFFF2E9E1, 0xFF9893A5, 0xFFD7827E,
                0xFFB4637A, 0x4AB4637A, 0xFF286983, 0xFF963D5A,
                0x66B4637A, 0x66963D5A,
                0xFFB4637A, 0xFF56949F, 0xFF286983, 0xFFEA9D34));
    }

    private static UiThemeDefinition definition(String id, String name, String author, Seed seed) {
        return new UiThemeDefinition(id, name, author, name + " color study",
                UiThemeRenderMode.PALETTE, UiThemeBuiltins.PIXEL_TEXTURE_SET, true,
                seed.toTokens());
    }

    /** 逐项种子强制每套研究覆盖完整令牌，避免预览因隐式回退而看起来“似乎可用”。 */
    private static final class Seed {
        private final int[] values;

        Seed(int... values) {
            if (values.length != UiThemeToken.values().length) {
                throw new IllegalArgumentException("light study expected "
                        + UiThemeToken.values().length + " colors, got " + values.length);
            }
            values = values.clone();
            this.values = values;
        }

        Map<UiThemeToken, UiColor> toTokens() {
            EnumMap<UiThemeToken, UiColor> result =
                    new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
            UiThemeToken[] tokens = UiThemeToken.values();
            for (int index = 0; index < tokens.length; index++) {
                result.put(tokens[index], new UiColor(values[index]));
            }
            return result;
        }
    }

    private UiThemeLightStudies() {
    }
}
