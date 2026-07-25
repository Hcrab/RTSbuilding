package com.rtsbuilding.rtsbuilding.server.service.mining;

/**
 * 生存平衡开启时，非连锁范围采掘允许触及的最高原版采集等级。
 *
 * <p>本枚举只承载跨版本稳定的规则数字；Forge 平台注册、物品和网络适配
 * 均留在各自边界，避免采掘规则继续依赖加载器 API。</p>
 */
public enum RangeMiningHarvestTier {
    STONE(1),
    IRON(2),
    DIAMOND(3),
    UNLIMITED(Integer.MAX_VALUE);

    private final int maxRequiredLevel;

    RangeMiningHarvestTier(int maxRequiredLevel) {
        this.maxRequiredLevel = maxRequiredLevel;
    }

    public int maxRequiredLevel() {
        return this.maxRequiredLevel;
    }

    public RangeMiningHarvestTier next() {
        RangeMiningHarvestTier[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
