package com.rtsbuilding.rtsbuilding;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.utils.UnmodifiableConfigWrapper;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRawSnapshot;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;

import java.util.function.Consumer;

/**
 * Forge SERVER spec 的薄适配器。Forge 在 {@code ModConfig.setConfigData} 中调用
 * {@link #acceptConfig(CommentedConfig)}，此时对象仍是 loader 刚读入的原文；适配器先做
 * 快照，再把纠正、typed 值和保存全部交给原始 {@link ForgeConfigSpec}。
 */
public final class RtsForgeServerConfigSpecAdapter
        extends UnmodifiableConfigWrapper<ForgeConfigSpec>
        implements IConfigSpec<RtsForgeServerConfigSpecAdapter> {
    private final ForgeConfigSpec delegate;
    private final Consumer<RtsServerConfigRawSnapshot> rawCapture;

    public RtsForgeServerConfigSpecAdapter(ForgeConfigSpec delegate) {
        this(delegate, Config::captureRawServerConfig);
    }

    /** 测试可注入快照接收器，但 correction/save 仍走真实 ForgeConfigSpec。 */
    public RtsForgeServerConfigSpecAdapter(ForgeConfigSpec delegate,
            Consumer<RtsServerConfigRawSnapshot> rawCapture) {
        super(delegate);
        if (delegate == null) throw new IllegalArgumentException("server config spec is required");
        if (rawCapture == null) throw new IllegalArgumentException("raw capture is required");
        this.delegate = delegate;
        this.rawCapture = rawCapture;
    }

    public ForgeConfigSpec delegate() {
        return delegate;
    }

    public String getLevelComment(java.util.List<String> path) {
        return delegate.getLevelComment(path);
    }

    public String getLevelTranslationKey(java.util.List<String> path) {
        return delegate.getLevelTranslationKey(path);
    }

    public void setConfig(CommentedConfig config) {
        delegate.setConfig(config);
    }

    @Override
    public void acceptConfig(CommentedConfig config) {
        rawCapture.accept(RtsServerConfigRawSnapshot.fromConfig(
                "forge:server-before-correction", config));
        delegate.acceptConfig(config);
    }

    @Override
    public boolean isCorrecting() {
        return delegate.isCorrecting();
    }

    public boolean isLoaded() {
        return delegate.isLoaded();
    }

    public UnmodifiableConfig getSpec() {
        return delegate.getSpec();
    }

    public UnmodifiableConfig getValues() {
        return delegate.getValues();
    }

    @Override
    public boolean isCorrect(CommentedConfig config) {
        return delegate.isCorrect(config);
    }

    @Override
    public int correct(CommentedConfig config) {
        return delegate.correct(config);
    }

    @Override
    public void afterReload() {
        delegate.afterReload();
    }

    public void save() {
        delegate.save();
    }
}
