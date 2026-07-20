package com.rtsbuilding.rtsbuilding.server.service.mining;

/**
 * 生存平衡开启时，非连锁范围挖掘允许触及的最高原版采集等级。
 *
 * <p>这里集中保存数字映射，业务代码只比较语义化档位。石质等级仍作为方块需求
 * 正常参与比较，但设置界面不单独暴露一档。</p>
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
