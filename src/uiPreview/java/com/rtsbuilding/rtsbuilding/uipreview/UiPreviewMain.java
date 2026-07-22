package com.rtsbuilding.rtsbuilding.uipreview;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;

/** 只输出 PNG 的 headless 预览入口；不会创建 JFrame 或任何可见窗口。 */
public final class UiPreviewMain {
    private UiPreviewMain() {
    }

    public static void main(String[] args) throws IOException {
        requireHeadless();
        File outputDirectory = outputDirectory(args);
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("Cannot create UI preview output directory: " + outputDirectory);
        }
        cleanGeneratedImages(outputDirectory);
        UiPreviewRenderer renderer = new UiPreviewRenderer();
        List<UiPreviewScenario> scenarios = UiPreviewScenario.firstBatch();
        for (UiPreviewScenario scenario : scenarios) {
            UiPreviewResult result = renderer.render(scenario);
            try {
                ImageIO.write(result.image(), "png", new File(outputDirectory, scenario.id() + ".png"));
            } finally {
                result.close();
            }
        }
        System.out.println("Rendered " + scenarios.size() + " headless UI preview scenes to " + outputDirectory);
    }

    static File outputDirectory(String[] args) {
        return new File(args.length == 0 ? "build/reports/ui-preview" : args[0]);
    }

    /** 清除专用报告目录里的旧预览和差异图，避免人工核实时混入已删除场景。 */
    static void cleanGeneratedImages(File outputDirectory) throws IOException {
        File[] generatedImages = outputDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png");
            }
        });
        if (generatedImages == null) {
            throw new IOException("Cannot list UI preview output directory: " + outputDirectory);
        }
        for (File generatedImage : generatedImages) {
            if (!generatedImage.delete()) {
                throw new IOException("Cannot remove stale UI preview image: " + generatedImage);
            }
        }
    }

    static void requireHeadless() {
        if (!Boolean.parseBoolean(System.getProperty("java.awt.headless", "false"))) {
            throw new IllegalStateException("UI preview must run with java.awt.headless=true");
        }
    }
}
