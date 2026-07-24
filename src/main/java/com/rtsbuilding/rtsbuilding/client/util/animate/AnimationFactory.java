package com.rtsbuilding.rtsbuilding.client.util.animate;


public final class AnimationFactory {

    

    
    public static final long HOVER_DURATION_MS = 120L;
    
    public static final long SLIDE_DURATION_MS = 200L;
    
    public static final long EXPAND_DURATION_MS = 300L;
    
    public static final long POPUP_DURATION_MS = 250L;
    
    public static final long FADE_DURATION_MS = 150L;

    private AnimationFactory() {}

    

    
    public static FloatAnimation newHoverAnim() {
        return FloatAnimation.builder()
                .duration(HOVER_DURATION_MS)
                .easing(EasingFunctions.SMOOTHSTEP)
                .startFromCurrent(true)
                .build();
    }

    
    public static FloatAnimation newSlideAnim() {
        return FloatAnimation.builder()
                .duration(SLIDE_DURATION_MS)
                .easing(EasingFunctions.EASE_OUT_BACK)
                .startFromCurrent(true)
                .build();
    }

    
    public static FloatAnimation newExpandAnim() {
        return FloatAnimation.builder()
                .duration(EXPAND_DURATION_MS)
                .easing(EasingFunctions.EASE_OUT_QUART)
                .startFromCurrent(true)
                .build();
    }

    
    public static FloatAnimation newPopupAnim() {
        return FloatAnimation.builder()
                .duration(POPUP_DURATION_MS)
                .easing(EasingFunctions.EASE_OUT_BACK)
                .startFromCurrent(true)
                .build();
    }

    
    public static FloatAnimation newFadeAnim() {
        return FloatAnimation.builder()
                .duration(FADE_DURATION_MS)
                .easing(EasingFunctions.LINEAR)
                .startFromCurrent(true)
                .build();
    }
}
