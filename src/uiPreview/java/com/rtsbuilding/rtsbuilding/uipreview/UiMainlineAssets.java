package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiPaletteTextureBaker;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 离屏预览对 main 资源目录的只读适配器。
 *
 * <p>贴图和翻译均按生产相对路径加载；找不到资源时立即失败，不用空矩形或
 * 硬编码英文掩盖主线资源漂移。</p>
 */
public final class UiMainlineAssets {
    private final File assetsRoot;
    private final Map<String, BufferedImage> images = new LinkedHashMap<String, BufferedImage>();
    private final Map<String, UiLanguageBundle> languages = new LinkedHashMap<String, UiLanguageBundle>();

    public UiMainlineAssets() {
        this(new File(System.getProperty("rts.ui.assets",
                "src/main/resources/assets/rtsbuilding")));
    }

    public UiMainlineAssets(File assetsRoot) {
        this.assetsRoot = assetsRoot;
    }

    public BufferedImage image(String relativePath) {
        BufferedImage cached = images.get(relativePath);
        if (cached != null) return cached;
        File file = new File(assetsRoot, relativePath.replace('/', File.separatorChar));
        try {
            BufferedImage loaded = ImageIO.read(file);
            if (loaded == null) throw new IOException("unsupported image format");
            images.put(relativePath, loaded);
            return loaded;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load mainline UI texture: " + file, error);
        }
    }

    public UiLanguageBundle language(String language) {
        UiLanguageBundle cached = languages.get(language);
        if (cached != null) return cached;
        File file = new File(new File(assetsRoot, "lang"), language + ".json");
        try {
            UiLanguageBundle loaded = UiLanguageBundle.load(file);
            if (loaded.size() < 900) throw new IOException("language file looks truncated");
            loaded = loaded.withFallback(loadMinecraftLanguage(language));
            languages.put(language, loaded);
            return loaded;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load mainline language: " + file, error);
        }
    }

    /** 读取真正的原版语言资源，让 gui.cancel 等文案与游戏一致。 */
    private static UiLanguageBundle loadMinecraftLanguage(String language) throws IOException {
        if ("en_us".equals(language)) {
            String clientJar = System.getProperty("rts.ui.preview.minecraftClientJar", "");
            if (!clientJar.isEmpty()) {
                return UiLanguageBundle.loadZipEntry(new File(clientJar),
                        "assets/minecraft/lang/en_us.json");
            }
        }
        String languageFile = System.getProperty(
                "rts.ui.preview.minecraftLanguage." + language, "");
        return languageFile.isEmpty() ? null : UiLanguageBundle.load(new File(languageFile));
    }

    public BufferedImage topBar(String name, String state) {
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        if (theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT) {
            return image("textures/gui/topbar/" + name + "_" + state + ".png");
        }
        String cacheKey = "generated/theme/" + theme.id() + "/topbar/" + name + "_" + state;
        BufferedImage cached = images.get(cacheKey);
        if (cached != null) return cached;
        BufferedImage source = image("textures/gui/topbar/" + name + "_hover.png");
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourceArgb = source.getRGB(0, 0, width, height, null, 0, width);
        UiIndexedTextureSpec spec = "quest_detect".equals(name)
                ? UiIndexedTextureSpec.PR133_QUEST : UiIndexedTextureSpec.PR133_THREE_TONE;
        int[] bakedArgb = UiPaletteTextureBaker.bake(
                sourceArgb, spec, theme, textureState(state));
        BufferedImage baked = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        baked.setRGB(0, 0, width, height, bakedArgb, 0, width);
        images.put(cacheKey, baked);
        return baked;
    }

    /**
     * 按生产目录解析 PR #133 形状图标：Legacy 读取贡献者四状态图，Palette 烘焙单张索引图。
     * 该入口不读取旧 450 像素形状图集，避免离屏审图与游戏实机显示成两套素材。
     */
    public BufferedImage quickBuildShape(QuickBuildUiShape shape, UiTextureState state) {
        if (shape == null || state == null) {
            throw new IllegalArgumentException("shape and state must not be null");
        }
        return quickBuildIcon(shape.contributorIconKey, state);
    }

    private static UiTextureState textureState(String state) {
        if ("active".equals(state)) return UiTextureState.ACTIVE;
        if ("hover".equals(state)) return UiTextureState.HOVER;
        if ("pressed".equals(state)) return UiTextureState.PRESSED;
        return UiTextureState.INACTIVE;
    }

    private static String textureStateName(UiTextureState state) {
        switch (state) {
            case HOVER: return "hover";
            case ACTIVE: return "active";
            case PRESSED: return "pressed";
            case INACTIVE:
            default: return "inactive";
        }
    }

    public BufferedImage item(String name) {
        return image("textures/item/" + name + ".png");
    }

    /**
     * 枚举主线实际存在的物品贴图，供底部终端场景生成正式资源条目。
     * 这里不伪造“钻石/石头”等占位项；资源增删会自然进入下一次截图。
     */
    public List<String> itemNames() {
        File folder = new File(assetsRoot, "textures" + File.separator + "item");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length == 0) {
            throw new IllegalStateException("Mainline item texture directory is empty: " + folder);
        }
        List<String> names = new ArrayList<String>();
        for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 4));
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }

    public BufferedImage guide(String name) {
        return image("textures/gui/guide/" + name + ".png");
    }

    public BufferedImage closeButton() {
        return image("textures/gui/general/close_button.png");
    }

    /** 通用按钮的两条主题轨道始终读取同一张 Legacy 原始母版。 */
    public BufferedImage defaultButton(UiTextureState state) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        BufferedImage source = image("textures/gui/general/default_button.png");
        if (theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT) return source;
        String cacheKey = "generated/theme/" + theme.id()
                + "/general/default_button_" + textureStateName(state);
        BufferedImage cached = images.get(cacheKey);
        if (cached != null) return cached;
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourceArgb = source.getRGB(0, 0, width, height, null, 0, width);
        int[] bakedArgb = UiPaletteTextureBaker.bake(
                sourceArgb,
                UiIndexedTextureSpec.LEGACY_DEFAULT_BUTTON,
                theme,
                state);
        BufferedImage baked = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        baked.setRGB(0, 0, width, height, bakedArgb, 0, width);
        images.put(cacheKey, baked);
        return baked;
    }

    /** 与生产 QuickBuildIconCatalog 使用相同键值解析便捷工具图标。 */
    public BufferedImage quickBuildConvenienceTool(QuickBuildUiConvenienceTool tool,
                                                    UiTextureState state) {
        String key;
        switch (tool) {
            case REPEAT_BOX:
                key = "cube";
                break;
            case CHUNK_QUARRY:
                key = "smart_break/stair";
                break;
            case TREE_FELL:
                key = "smart_break/tree";
                break;
            default:
                throw new IllegalArgumentException("Unsupported convenience tool: " + tool);
        }
        return quickBuildIcon(key, state);
    }

    /** 与生产 Smart Fill 按钮共用同一套四状态图标。 */
    public BufferedImage quickBuildSmartFill(UiTextureState state) {
        return quickBuildIcon("fill_water/cave", state);
    }

    private BufferedImage quickBuildIcon(String key, UiTextureState state) {
        if (key == null || state == null) {
            throw new IllegalArgumentException("key and state must not be null");
        }
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        if (theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT) {
            return image("textures/gui/quickbuild_pr133/" + key + "_"
                    + textureStateName(state) + ".png");
        }
        String cacheKey = "generated/theme/" + theme.id() + "/quickbuild/" + key
                + "_" + textureStateName(state);
        BufferedImage cached = images.get(cacheKey);
        if (cached != null) return cached;
        BufferedImage source = image("textures/gui/new_2nd_icons/" + key + ".png");
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourceArgb = source.getRGB(0, 0, width, height, null, 0, width);
        int[] bakedArgb = UiPaletteTextureBaker.bake(sourceArgb,
                UiIndexedTextureSpec.PR133_THREE_TONE, theme, state);
        BufferedImage baked = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        baked.setRGB(0, 0, width, height, bakedArgb, 0, width);
        images.put(cacheKey, baked);
        return baked;
    }

    /** Quick Build 小开关始终读取 Legacy mode_button 图集；Palette 只替换原像素颜色。 */
    public BufferedImage quickBuildIndicator(UiTextureState state) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        BufferedImage source = image("textures/gui/general/mode_button.png");
        if (theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT) return source;
        String cacheKey = "generated/theme/" + theme.id()
                + "/general/mode_button_" + textureStateName(state);
        BufferedImage cached = images.get(cacheKey);
        if (cached != null) return cached;
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourceArgb = source.getRGB(0, 0, width, height, null, 0, width);
        int[] bakedArgb = UiPaletteTextureBaker.bake(
                sourceArgb,
                UiIndexedTextureSpec.LEGACY_MODE_BUTTON,
                theme,
                state);
        BufferedImage baked = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        baked.setRGB(0, 0, width, height, bakedArgb, 0, width);
        images.put(cacheKey, baked);
        return baked;
    }

    /** 设置开关始终使用 Legacy 四态图集；Palette 主题只烘焙替换其索引色。 */
    public BufferedImage settingsSwitch(UiTextureState state) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        BufferedImage source = image("textures/gui/general/switch_button.png");
        if (theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT) return source;
        String cacheKey = "generated/theme/" + theme.id()
                + "/general/switch_button_" + textureStateName(state);
        BufferedImage cached = images.get(cacheKey);
        if (cached != null) return cached;
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourceArgb = source.getRGB(0, 0, width, height, null, 0, width);
        int[] bakedArgb = UiPaletteTextureBaker.bake(
                sourceArgb,
                UiIndexedTextureSpec.LEGACY_SETTINGS_SWITCH,
                theme,
                state);
        BufferedImage baked = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        baked.setRGB(0, 0, width, height, bakedArgb, 0, width);
        images.put(cacheKey, baked);
        return baked;
    }
}
