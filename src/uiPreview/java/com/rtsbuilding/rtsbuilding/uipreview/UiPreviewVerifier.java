package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.performance.UiPerformanceBudget;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 运行结构断言、九宫格预算和同进程确定性检查。 */
public final class UiPreviewVerifier {
    private UiPreviewVerifier() {
    }

    public static void main(String[] args) throws IOException {
        UiPreviewMain.requireHeadless();
        verifyDiffEngine();
        UiScenarioInputVerifier.verify();
        File outputDirectory = UiPreviewMain.outputDirectory(args);
        File baselineDirectory = new File(args.length < 2 ? "src/uiPreviewSnapshots" : args[1]);
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("Cannot create UI preview output directory: " + outputDirectory);
        }
        UiPreviewMain.cleanGeneratedImages(outputDirectory);

        UiPreviewRenderer renderer = new UiPreviewRenderer();
        Set<UiPreviewScenario.Variant> variants = new HashSet<UiPreviewScenario.Variant>();
        for (UiPreviewScenario scenario : UiPreviewScenario.firstBatch()) {
            if (!variants.add(scenario.variant())) {
                throw new IllegalStateException("duplicate preview variant: " + scenario.variant());
            }
            UiPreviewResult first = renderer.render(scenario);
            UiPreviewResult second = renderer.render(scenario);
            try {
                verifyStructure(first);
                verifyDeterministic(first.image(), second.image(), scenario.id());
                File output = new File(outputDirectory, scenario.id() + ".png");
                ImageIO.write(first.image(), "png", output);
                if (!output.isFile() || output.length() == 0L) {
                    throw new IllegalStateException("missing snapshot output: " + output);
                }
                verifyApprovedBaseline(first.image(), baselineDirectory, outputDirectory, scenario.id());
            } finally {
                first.close();
                second.close();
            }
        }
        System.out.println("Verified " + UiPreviewScenario.firstBatch().size()
                + " deterministic headless UI preview scenes");
    }

    private static void verifyStructure(UiPreviewResult result) {
        UiRect screen = result.layout().screen();
        if (!screen.contains(result.layout().topBar())
                || !screen.contains(result.layout().bottomBar())
                || !screen.contains(result.layout().jadeReserve())) {
            throw new IllegalStateException("fixed UI area escaped screen bounds: screen=" + screen
                    + ", top=" + result.layout().topBar()
                    + ", bottom=" + result.layout().bottomBar()
                    + ", jade=" + result.layout().jadeReserve());
        }
        if (result.layout().topBar().intersects(result.layout().bottomBar())
                || result.layout().jadeReserve().intersects(result.layout().topBar())
                || result.layout().jadeReserve().intersects(result.layout().bottomBar())) {
            throw new IllegalStateException("reserved fixed UI regions overlap");
        }
        for (UiPreviewLayout.NamedPanel panel : result.layout().panels()) {
            if (!screen.contains(panel.bounds()) || panel.bounds().isEmpty()) {
                throw new IllegalStateException("invalid panel bounds: " + panel.id());
            }
            if (panel.bounds().getWidth() < 40 || panel.bounds().getHeight() < 40) {
                throw new IllegalStateException("panel hit area is too small: " + panel.id());
            }
            UiRect scissor = new UiRect(panel.bounds().getX() + 5, panel.bounds().getY() + 29,
                    panel.bounds().getWidth() - 10, panel.bounds().getHeight() - 34);
            UiRect closeHitbox = new UiRect(panel.bounds().right() - 23,
                    panel.bounds().getY() + 8, 14, 14);
            if (!panel.bounds().contains(scissor) || !panel.bounds().contains(closeHitbox)) {
                throw new IllegalStateException("panel scissor or close hitbox escaped bounds: " + panel.id());
            }
        }
        if (result.maximumNineSliceQuads() > 9) {
            throw new IllegalStateException("nine-slice exceeded nine quadrilaterals");
        }
        if (result.primitiveCount() <= 0 || result.primitiveCount() > 1400) {
            throw new IllegalStateException("unexpected UI primitive budget: " + result.primitiveCount());
        }
        new UiPerformanceBudget(1400, 45, 0, 1, 80, 0).verify(result.statsSnapshot());
    }

    private static void verifyDeterministic(BufferedImage first, BufferedImage second, String id) {
        UiSnapshotDiff.Result diff = UiSnapshotDiff.compare(first, second);
        if (diff.changedPixels() != 0L) {
            throw new IllegalStateException("non-deterministic headless render: " + id);
        }
    }

    private static void verifyApprovedBaseline(BufferedImage actual, File baselineDirectory,
                                               File outputDirectory, String id) throws IOException {
        File baseline = new File(baselineDirectory, id + ".png");
        if (!baseline.isFile()) {
            return;
        }
        BufferedImage expected = ImageIO.read(baseline);
        UiSnapshotDiff.Result diff = UiSnapshotDiff.compare(expected, actual);
        if (diff.changedPixels() != 0L) {
            File heatmap = new File(outputDirectory, id + ".diff.png");
            ImageIO.write(diff.heatmap(), "png", heatmap);
            throw new IllegalStateException("approved snapshot changed: " + id
                    + " (" + diff.changedPixels() + " pixels, heatmap " + heatmap + ")");
        }
    }

    private static void verifyDiffEngine() {
        BufferedImage expected = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage actual = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        if (UiSnapshotDiff.compare(expected, actual).changedPixels() != 0L) {
            throw new IllegalStateException("snapshot diff reports false positive");
        }
        actual.setRGB(1, 1, 0xFFFFFFFF);
        if (UiSnapshotDiff.compare(expected, actual).changedPixels() != 1L) {
            throw new IllegalStateException("snapshot diff missed changed pixel");
        }
        if (UiSnapshotDiff.compare(expected, actual,
                Collections.singletonList(new UiRect(1, 1, 1, 1))).changedPixels() != 0L) {
            throw new IllegalStateException("snapshot mask did not exclude changed pixel");
        }
    }
}
