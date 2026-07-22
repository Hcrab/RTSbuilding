package com.rtsbuilding.rtsbuilding.uipreview;

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
            languages.put(language, loaded);
            return loaded;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load mainline language: " + file, error);
        }
    }

    public BufferedImage topBar(String name, String state) {
        return image("textures/gui/topbar/" + name + "_" + state + ".png");
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

    public BufferedImage quickBuild(String name) {
        return image("textures/gui/quickbuild/" + name + ".png");
    }

    public BufferedImage closeButton() {
        return image("textures/gui/general/close_button.png");
    }
}
