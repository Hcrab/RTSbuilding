import javafx.application.Platform;
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.util.FileSaver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * 调用与目标 HMCL 完全相同的 MultiMC 导入核心，把官方 GTNH 包转换成
 * HMCL 原生版本目录。它只负责首次导入，不保存账号，也不负责启动游戏。
 */
public final class HmclModpackImporter {
    private static final String READY_MARKER = ".rtsbuilding-hmcl-ready";

    private HmclModpackImporter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
        }

        String operation = args[0];
        if ("import".equals(operation) && args.length != 4) {
            usage();
        }
        if ("duplicate".equals(operation) && args.length != 4) {
            usage();
        }
        if ("finalize".equals(operation) && args.length != 3) {
            usage();
        }

        Path gameDir = Path.of(args[1]).toAbsolutePath().normalize();
        startJavaFx();
        try {
            ConfigHolder.init();
            if ("import".equals(operation)) {
                importModpack(gameDir, Path.of(args[2]), args[3]);
            } else if ("duplicate".equals(operation)) {
                duplicateInstance(gameDir, args[2], args[3]);
            } else if ("finalize".equals(operation)) {
                finalizeInstance(gameDir, args[2]);
            } else {
                usage();
            }
        } finally {
            FileSaver.shutdown();
            Schedulers.shutdown();
            Platform.exit();
        }
    }

    private static void importModpack(Path gameDir, Path zipArgument, String instanceName)
            throws Exception {
        Path modpackZip = zipArgument.toAbsolutePath().normalize();
        if (!Files.isRegularFile(modpackZip)) {
            throw new IllegalArgumentException("GTNH modpack zip does not exist: " + modpackZip);
        }
        Profile profile = newProfile(gameDir, instanceName);
        HMCLGameRepository repository = profile.getRepository();
        repository.refreshVersions();

        Modpack modpack = ModpackHelper.readModpackManifest(
                modpackZip, StandardCharsets.UTF_8);
        Task<?> install = ModpackHelper.getInstallTask(
                profile, modpackZip, instanceName, modpack, null);
        TaskExecutor executor = install.executor(new TaskListener() {
            @Override
            public void onFailed(Task<?> task, Throwable throwable) {
                System.err.println("[RTSBuilding] HMCL task failed: " + task.getName());
                throwable.printStackTrace(System.err);
            }
        });
        if (!executor.test()) {
            Exception failure = executor.getException();
            if (failure != null) {
                throw failure;
            }
            throw new IllegalStateException("HMCL import was cancelled: " + instanceName);
        }

        finishInstance(repository, instanceName);
        System.out.println("[RTSBuilding] HMCL import completed: " + instanceName);
    }

    private static void duplicateInstance(Path gameDir, String source, String target)
            throws Exception {
        Profile profile = newProfile(gameDir, source);
        HMCLGameRepository repository = profile.getRepository();
        repository.refreshVersions();
        if (!repository.hasVersion(source)) {
            throw new IllegalArgumentException("HMCL source instance does not exist: " + source);
        }
        if (!repository.hasVersion(target)) {
            repository.duplicateVersion(source, target, false);
        }
        repository.refreshVersions();
        finishInstance(repository, target);
        System.out.println("[RTSBuilding] HMCL duplicate completed: " + target);
    }

    private static Profile newProfile(Path gameDir, String selectedVersion) {
        return new Profile("RTSBuilding-GTNH", gameDir,
                new VersionSetting(), selectedVersion, false);
    }

    private static void finalizeInstance(Path gameDir, String instanceName) throws Exception {
        Profile profile = newProfile(gameDir, instanceName);
        HMCLGameRepository repository = profile.getRepository();
        repository.refreshVersions();
        finishInstance(repository, instanceName);
        System.out.println("[RTSBuilding] HMCL instance finalized: " + instanceName);
    }

    private static void finishInstance(HMCLGameRepository repository, String instanceName)
            throws Exception {
        if (!repository.hasVersion(instanceName)) {
            throw new IllegalStateException(
                    "HMCL operation finished without creating version: " + instanceName);
        }
        VersionSetting setting = Objects.requireNonNull(
                repository.specializeVersionSetting(instanceName));
        setting.setUsesGlobal(false);
        setting.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        setting.setMaxMemory(Math.max(setting.getMaxMemory(), 8192));
        repository.saveVersionSetting(instanceName);
        // 标记内容包含实例名，避免复制 CLEAN 时把尚未完成的 SIMPLE 误判为可启动。
        Files.writeString(repository.getVersionRoot(instanceName).resolve(READY_MARKER),
                instanceName + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static void usage() {
        throw new IllegalArgumentException(
                "Usage: import <game-dir> <modpack-zip> <instance-name> | "
                        + "duplicate <game-dir> <source-name> <target-name> | "
                        + "finalize <game-dir> <instance-name>");
    }

    private static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
    }
}
