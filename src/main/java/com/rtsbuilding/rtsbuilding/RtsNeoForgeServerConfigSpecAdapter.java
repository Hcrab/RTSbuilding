package com.rtsbuilding.rtsbuilding;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRawSnapshot;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Consumer;

/**
 * NeoForge SERVER spec 的薄时序适配器。
 *
 * <p>NeoForge 会先调用 {@link #isCorrect(UnmodifiableCommentedConfig)}，再调用
 * {@link #correct(CommentedConfig)} 并写回文件；这里仅在前一步保存原始对象，再把所有
 * 修正、加载和保存行为委托给真实 {@link ModConfigSpec}。不在 loader 回调里做世界迁移。</p>
 */
public final class RtsNeoForgeServerConfigSpecAdapter implements IConfigSpec {
    private final ModConfigSpec delegate;
    private final Consumer<RtsServerConfigRawSnapshot> rawCapture;

    public RtsNeoForgeServerConfigSpecAdapter(ModConfigSpec delegate) {
        this(delegate, Config::captureRawServerConfig);
    }

    /** 测试可注入快照接收器，但 correction/save 仍走真实 ModConfigSpec。 */
    public RtsNeoForgeServerConfigSpecAdapter(ModConfigSpec delegate,
            Consumer<RtsServerConfigRawSnapshot> rawCapture) {
        if (delegate == null) throw new IllegalArgumentException("server config spec is required");
        if (rawCapture == null) throw new IllegalArgumentException("raw capture is required");
        this.delegate = delegate;
        this.rawCapture = rawCapture;
    }

    public ModConfigSpec delegate() {
        return delegate;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public void validateSpec(net.neoforged.fml.config.ModConfig config) {
        delegate.validateSpec(config);
    }

    @Override
    public boolean isCorrect(UnmodifiableCommentedConfig config) {
        rawCapture.accept(RtsServerConfigRawSnapshot.fromConfig(
                "neoforge:server-before-correction", config));
        return delegate.isCorrect(config);
    }

    @Override
    public void correct(CommentedConfig config) {
        delegate.correct(config);
    }

    @Override
    public void acceptConfig(IConfigSpec.ILoadedConfig config) {
        delegate.acceptConfig(config);
    }
}
