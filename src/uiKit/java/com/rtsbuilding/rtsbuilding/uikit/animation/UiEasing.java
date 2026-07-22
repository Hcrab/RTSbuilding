package com.rtsbuilding.rtsbuilding.uikit.animation;

/** 常用的无分配单值缓动函数。 */
public enum UiEasing {
    LINEAR {
        @Override
        public double apply(double progress) {
            return clamp(progress);
        }
    },
    EASE_OUT_CUBIC {
        @Override
        public double apply(double progress) {
            double inverse = 1.0D - clamp(progress);
            return 1.0D - inverse * inverse * inverse;
        }
    },
    EASE_IN_OUT_QUAD {
        @Override
        public double apply(double progress) {
            double value = clamp(progress);
            return value < 0.5D ? 2.0D * value * value
                    : 1.0D - Math.pow(-2.0D * value + 2.0D, 2.0D) / 2.0D;
        }
    };

    public abstract double apply(double progress);

    static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
