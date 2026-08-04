package com.rtsbuilding.rtsbuilding.client.entity;

// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.19 or later with Mojang mappings

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * 无人机动画定义（Blockbench 导出，时间已按秒 → tick 换算，1 秒 = 20 tick）。
 * <p>
 * fly：四旋翼高速旋转动画，0.25 秒 / 圈（每秒 1440°，即 5 tick / 圈，72° / tick），循环播放。
 * <p>
 * 注意：当前 {@link rts_drone#setupAnim} 中为消除 {@code KeyframeAnimations.animate} 的
 * long 时间截断导致的 20Hz 跳变卡顿，已改为手写 float 旋转实现（速度与此定义完全一致）。
 * 本类保留作为动画定义参考；若后续动画复杂化（多关键帧/多骨骼），可切回动画 API 播放。
 */
public class rts_droneAnimation {
	public static final AnimationDefinition fly = AnimationDefinition.Builder.withLength(5.0F).looping()
		.addAnimation("blade_fl", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("blade_bl", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("blade_fr", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("blade_br", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();
}
