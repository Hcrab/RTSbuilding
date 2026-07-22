package com.rtsbuilding.rtsbuilding.uipreview;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * 显式更新快照基线的受保护入口。
 *
 * <p>只有同时设置 headless 和 {@code rts.ui.snapshots.approved=true} 才会写入，
 * 普通 check 永远不会调用它。</p>
 */
public final class UiSnapshotUpdater {
    private UiSnapshotUpdater() {
    }

    public static void main(String[] args) throws IOException {
        UiPreviewMain.requireHeadless();
        if (!Boolean.parseBoolean(System.getProperty("rts.ui.snapshots.approved", "false"))) {
            throw new IllegalStateException("snapshot update requires explicit human approval");
        }
        File directory = UiPreviewMain.outputDirectory(args);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create snapshot baseline directory: " + directory);
        }
        UiPreviewRenderer renderer = new UiPreviewRenderer();
        for (UiPreviewScenario scenario : UiPreviewScenario.firstBatch()) {
            UiPreviewResult result = renderer.render(scenario);
            try {
                ImageIO.write(result.image(), "png", new File(directory, scenario.id() + ".png"));
            } finally {
                result.close();
            }
        }
        System.out.println("Updated " + UiPreviewScenario.firstBatch().size()
                + " approved UI snapshot baselines in " + directory);
    }
}
