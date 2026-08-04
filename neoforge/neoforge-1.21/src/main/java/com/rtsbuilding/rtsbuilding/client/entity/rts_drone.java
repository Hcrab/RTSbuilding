package com.rtsbuilding.rtsbuilding.client.entity;

// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.Easing;
import com.rtsbuilding.rtsbuilding.common.entity.RtsDroneEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;


public class rts_drone<T extends Entity> extends HierarchicalModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	// 注意：layer 名称必须与 RtsDroneRenderer.LAYER_LOCATION 保持一致（注册/烘焙均以渲染器常量为准）
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rts_drone"), "main");
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart body_core;
	private final ModelPart body_top;
	private final ModelPart landing_gear;
	private final ModelPart rotor_arms;
	private final ModelPart rotors;
	private final ModelPart rotor_bl;
	private final ModelPart blade_bl;
	private final ModelPart rotor_br;
	private final ModelPart blade_br;
	private final ModelPart rotor_fr;
	private final ModelPart blade_fr;
	private final ModelPart rotor_fl;
	private final ModelPart blade_fl;
	private final ModelPart camera;

	public rts_drone(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.body_core = this.body.getChild("body_core");
		this.body_top = this.body.getChild("body_top");
		this.landing_gear = this.body.getChild("landing_gear");
		this.rotor_arms = this.body.getChild("rotor_arms");
		this.rotors = this.body.getChild("rotors");
		this.rotor_bl = this.rotors.getChild("rotor_bl");
		this.blade_bl = this.rotor_bl.getChild("blade_bl");
		this.rotor_br = this.rotors.getChild("rotor_br");
		this.blade_br = this.rotor_br.getChild("blade_br");
		this.rotor_fr = this.rotors.getChild("rotor_fr");
		this.blade_fr = this.rotor_fr.getChild("blade_fr");
		this.rotor_fl = this.rotors.getChild("rotor_fl");
		this.blade_fl = this.rotor_fl.getChild("blade_fl");
		this.camera = this.body.getChild("camera");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(-1.055F, 15.84F, 0.8378F));

		PartDefinition body_core = body.addOrReplaceChild("body_core", CubeListBuilder.create().texOffs(4, 60).addBox(-2.0F, 0.0F, 1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(48, 9).addBox(-2.0F, -0.6F, -0.5F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 60).addBox(1.0F, 0.0F, 1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.055F, 1.16F, -2.3378F));

		PartDefinition cube_r1 = body_core.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(48, 9).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 1.5F, 0.0F, -0.7854F, 0.0F));

		PartDefinition body_top = body.addOrReplaceChild("body_top", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, 1.0F, -6.0F, 12.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 25).addBox(-4.0F, 3.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(1.055F, -2.84F, -0.8378F));

		PartDefinition cube_r2 = body_top.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 14).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(32, 25).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition landing_gear = body.addOrReplaceChild("landing_gear", CubeListBuilder.create(), PartPose.offset(-4.22F, 1.16F, 4.4372F));

		PartDefinition cube_r3 = landing_gear.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(52, 44).addBox(-4.5F, -1.0F, -0.5F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.55F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition cube_r4 = landing_gear.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(32, 57).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.55F, 0.0F, -10.55F, 0.0F, -0.7854F, 0.0F));

		PartDefinition cube_r5 = landing_gear.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(56, 57).addBox(-0.5F, -1.0F, -0.5F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -10.55F, 0.0F, -0.7854F, 0.0F));

		PartDefinition cube_r6 = landing_gear.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(44, 57).addBox(-0.5F, -1.0F, -4.5F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition rotor_arms = body.addOrReplaceChild("rotor_arms", CubeListBuilder.create(), PartPose.offset(1.055F, 0.26F, -0.6307F));

		PartDefinition cube_r7 = rotor_arms.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 47).addBox(-15.753F, -2.5F, -1.1464F, 11.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.7854F, 3.1416F));

		PartDefinition cube_r8 = rotor_arms.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(32, 40).addBox(-15.753F, -2.5F, -1.1464F, 11.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition cube_r9 = rotor_arms.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(26, 44).addBox(-15.753F, -2.5F, -1.1464F, 11.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition cube_r10 = rotor_arms.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 43).addBox(-15.753F, -2.5F, -1.1464F, 11.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition rotors = body.addOrReplaceChild("rotors", CubeListBuilder.create(), PartPose.offset(1.055F, 0.26F, -0.6307F));

		PartDefinition rotor_bl = rotors.addOrReplaceChild("rotor_bl", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r11 = rotor_bl.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(56, 23).addBox(-14.753F, -4.5F, -1.1464F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -0.7F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r12 = rotor_bl.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(56, 23).addBox(-14.753F, -4.5F, -1.1464F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.7F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r13 = rotor_bl.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(42, 48).addBox(-16.7531F, -3.5F, -2.1465F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(58, 53).addBox(-15.753F, 1.5F, -1.1464F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition blade_bl = rotor_bl.addOrReplaceChild("blade_bl", CubeListBuilder.create(), PartPose.offset(10.5355F, -4.4F, -10.3284F));

		PartDefinition cube_r14 = blade_bl.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(40, 20).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition cube_r15 = blade_bl.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(40, 17).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 3.1416F, -0.7854F, -3.1416F));

		PartDefinition rotor_br = rotors.addOrReplaceChild("rotor_br", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r16 = rotor_br.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(56, 23).addBox(-14.7531F, -4.5F, -1.1464F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-20.875F, -1.0F, -0.9F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r17 = rotor_br.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(8, 60).addBox(-14.7531F, -4.5F, -1.1464F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-20.875F, 0.0F, -0.9F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r18 = rotor_br.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(58, 53).addBox(-15.753F, 1.5F, -1.1464F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(42, 48).addBox(-16.7531F, -3.5F, -2.1465F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition blade_br = rotor_br.addOrReplaceChild("blade_br", CubeListBuilder.create(), PartPose.offset(-10.3395F, -4.4F, -10.5284F));

		PartDefinition cube_r19 = blade_br.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(40, 20).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 3.1416F, -0.7854F, -3.1416F));

		PartDefinition cube_r20 = blade_br.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(40, 17).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition rotor_fr = rotors.addOrReplaceChild("rotor_fr", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r21 = rotor_fr.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(56, 23).addBox(-14.753F, -4.5F, -1.1465F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-21.05F, -1.0F, 19.925F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r22 = rotor_fr.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(8, 60).addBox(-14.753F, -4.5F, -1.1465F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-21.05F, 0.0F, 19.925F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r23 = rotor_fr.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(42, 48).addBox(-16.7531F, -3.5F, -2.1465F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(58, 53).addBox(-15.753F, 1.5F, -1.1464F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition blade_fr = rotor_fr.addOrReplaceChild("blade_fr", CubeListBuilder.create(), PartPose.offset(-10.5145F, -4.4F, 10.2966F));

		PartDefinition cube_r24 = blade_fr.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(40, 20).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 3.1416F, -0.7854F, -3.1416F));

		PartDefinition cube_r25 = blade_fr.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(40, 17).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition rotor_fl = rotors.addOrReplaceChild("rotor_fl", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r26 = rotor_fl.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(42, 48).addBox(-16.7531F, -3.5F, -2.1465F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(58, 53).addBox(-15.753F, 1.5F, -1.1464F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.7854F, 3.1416F));

		PartDefinition cube_r27 = rotor_fl.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(56, 23).addBox(-14.753F, -4.5F, -1.1465F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2F, -1.0F, 20.175F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition cube_r28 = rotor_fl.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(8, 60).addBox(-14.753F, -4.5F, -1.1465F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2F, 0.0F, 20.175F, -3.1416F, -0.7854F, 3.1416F));

		PartDefinition blade_fl = rotor_fl.addOrReplaceChild("blade_fl", CubeListBuilder.create(), PartPose.offset(10.3355F, -4.4F, 10.5466F));

		PartDefinition cube_r29 = blade_fl.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(40, 17).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 3.1416F, -0.7854F, -3.1416F));

		PartDefinition cube_r30 = blade_fl.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(40, 20).addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		// 相机立方体居中到旋转原点（部件原点 = 立方体中心），使其像云台一样原地俯仰；
		// 若保持旧的偏移，旋转会绕远离立方体的铰点产生摆动弧，放大"压缩拉伸"的错觉。
		PartDefinition camera = body.addOrReplaceChild("camera", CubeListBuilder.create().texOffs(16, 51).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(1.055F, 3.16F, -0.8378F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	/** 根部件（HierarchicalModel 抽象方法，动画系统按骨骼名递归查找时用到） */
	@Override
	public ModelPart root() {
		return this.root;
	}

	/** 螺旋桨转速（度/tick）：与 {@link rts_droneAnimation#fly} 一致，5 tick/圈 = 0.25 秒/圈 */
	private static final float BLADE_ROTATION_PER_TICK = 360.0F / 5.0F;

	/** 相机云台最大俯仰角（度）：限制在正负范围内，避免镜头翻转；过大（接近 90°）时立方体几乎侧面朝上、
	 *  透视投影会把镜头"压扁"成一条窄面，看起来像被压缩拉伸 */
	private static final float CAMERA_MAX_PITCH_DEG = 60.0F;
	/** 动画时间平滑时长（毫秒）：对上一包/当前包插值输出再做一层 AnimFloat 时间平滑，抗多人联机网络抖动 */
	private static final long ANIM_SMOOTH_MS = 120L;

	/** 每实体动画时间平滑状态（AnimFloat），模型实例跨实体共享故按实体缓存 */
	private final Map<Entity, DroneAnim> droneAnims = new WeakHashMap<>();

	/** 每个实体的 AnimFloat 平滑器：机身倾角（俯仰/横滚）、云台俯仰、机身偏航 */
	private static final class DroneAnim {
		final AnimFloat tiltX = AnimFloat.of(0.0F, ANIM_SMOOTH_MS, Easing.EASE_OUT_CUBIC);
		final AnimFloat tiltZ = AnimFloat.of(0.0F, ANIM_SMOOTH_MS, Easing.EASE_OUT_CUBIC);
		final AnimFloat pitch = AnimFloat.of(0.0F, ANIM_SMOOTH_MS, Easing.EASE_OUT_CUBIC);
		final AnimFloat yaw = AnimFloat.of(0.0F, ANIM_SMOOTH_MS, Easing.EASE_OUT_CUBIC);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		// 每帧重置姿态（为后续其他骨骼动画预留）
		this.root.getAllParts().forEach(ModelPart::resetPose);
		// 四旋翼匀速旋转。手写 float 计算而非 KeyframeAnimations.animate：
		// 其 long 时间参数会截断小数，动画被锁在 20Hz，高刷新率屏幕上 3 帧静止 1 帧跳转，
		// 表现为视觉变慢 + 卡顿；float 时间每渲染帧平滑推进（60fps 下每帧约 24°）。
		float turn = Mth.DEG_TO_RAD * -(ageInTicks % 5.0F) * BLADE_ROTATION_PER_TICK;
		this.blade_fl.yRot = turn;
		this.blade_bl.yRot = turn;
		this.blade_fr.yRot = turn;
		this.blade_br.yRot = turn;
		// 移动/相机动画：动画状态由服务端每 tick 的 S2CRtsDroneAnimPayload 网络包同步到
		// {@link RtsDroneEntity} 的 prev/current 缓存；先对上一包/当前包做 partialTick + 缓动插值
		// （tick 对齐、单机平滑），再把插值输出喂给每实体 AnimFloat 做一层时间平滑（抗多人联机网络抖动）。
		if (entity instanceof RtsDroneEntity drone) {
			float easedTick = Easing.SMOOTHSTEP.apply(Mth.clamp(limbSwingAmount, 0.0F, 1.0F));
			// 基础插值目标：上一包 → 当前包
			float targetTiltX = Mth.lerp(easedTick, drone.animPrevTiltX, drone.animCurrTiltX);
			float targetTiltZ = Mth.lerp(easedTick, drone.animPrevTiltZ, drone.animCurrTiltZ);
			float targetPitch = Mth.lerp(easedTick, drone.animPrevPitch, drone.animCurrPitch);
			float targetYaw = Mth.rotLerp(easedTick, drone.animPrevYaw, drone.animCurrYaw);

			// 时间平滑层：AnimFloat（时间基、帧率无关）对插值输出再平滑。
			// 必须先 get() 推进到当前时间再 target()：否则每帧 target() 会把 startTime 重置、
			// from 置为当前值，get() 时 elapsed≈0、easing(0)=0，动画会一直卡在原地不动。
			DroneAnim anim = this.droneAnims.computeIfAbsent(entity, e -> new DroneAnim());
			anim.tiltX.get();
			anim.tiltZ.get();
			anim.pitch.get();
			// 偏航是环形角：先取当前值，再相对它取最短路径目标（wrapDegrees），
			// 否则相机 yaw 跨 ±180° 时（如 179 → -179）会被当成 360° 大回旋，机身猛转一整圈。
			float yawNow = anim.yaw.get();

			anim.tiltX.target(targetTiltX);
			anim.tiltZ.target(targetTiltZ);
			anim.pitch.target(targetPitch);
			anim.yaw.target(yawNow + Mth.wrapDegrees(targetYaw - yawNow));

			// 机身倾角（俯仰/横滚）
			this.body.xRot = anim.tiltX.get() * Mth.DEG_TO_RAD;
			this.body.zRot = anim.tiltZ.get() * Mth.DEG_TO_RAD;
			// 相机云台俯仰（负值向上、正值向下，MC 相机俯仰约定；限幅避免镜头翻转）
			float camPitch = Mth.clamp(anim.pitch.get(), -CAMERA_MAX_PITCH_DEG, CAMERA_MAX_PITCH_DEG);
			this.camera.xRot = -camPitch * Mth.DEG_TO_RAD;
			// 机身偏航
			this.body.yRot = anim.yaw.get() * Mth.DEG_TO_RAD;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}
