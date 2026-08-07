package com.rtsbuilding.rtsbuilding.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 可选模组 Mixin 的类加载门卫。
 *
 * <p>它不实现兼容行为，只保证外部模组没有安装时，Mixin 不会尝试解析该模组的类。 其余原版目标 Mixin 一律照常加载。
 */
public final class RtsOptionalCompatMixinConfigPlugin implements IMixinConfigPlugin {
  @Override
  public void onLoad(String mixinPackage) {}

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    FabricLoader loader = FabricLoader.getInstance();
    if (mixinClassName.endsWith("IronFurnacesRemoteStillValidMixin")) {
      return loader.isModLoaded("ironfurnaces");
    }
    if (mixinClassName.endsWith("GeneratorGaloreRemoteStillValidMixin")) {
      return loader.isModLoaded("generatorgalore");
    }
    if (mixinClassName.endsWith("SophisticatedBackpacksRemoteStillValidMixin")) {
      return loader.isModLoaded("sophisticatedbackpacks");
    }
    if (mixinClassName.endsWith("SophisticatedStorageRemoteStillValidMixin")) {
      return loader.isModLoaded("sophisticatedstorage");
    }
    if (mixinClassName.endsWith("EmbeddiumWorldSliceMixin")) {
      return loader.isModLoaded("embeddium");
    }
    if (mixinClassName.endsWith("SodiumLevelSliceMixin")) {
      return loader.isModLoaded("sodium");
    }
    if (mixinClassName.endsWith("FlywheelBlockEntityStorageMixin")) {
      return loader.isModLoaded("flywheel");
    }
    return true;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
