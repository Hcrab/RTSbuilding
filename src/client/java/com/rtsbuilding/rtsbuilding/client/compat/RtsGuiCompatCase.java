package com.rtsbuilding.rtsbuilding.client.compat;

/**
 * 一个远程 GUI 客户端探针案例。
 *
 * <p>它只描述测试意图，不持有 Minecraft 对象，也不负责准备世界。这样案例清单可以在普通单元测试里严格校验， 而生产探针仍然只负责逐 tick 驱动真实 RTS 交互。
 */
record RtsGuiCompatCase(
    String id,
    String blockId,
    int distance,
    String depth,
    String setupAdapter,
    int setupWaitTicks,
    String interactionItemId,
    String hitFace,
    double hitOffsetX,
    double hitOffsetY,
    double hitOffsetZ,
    String expectedMenuRegex,
    String expectedScreenRegex) {

  RtsGuiCompatCase {
    id = requireText(id, "id");
    blockId = requireText(blockId, "blockId");
    distance = Math.max(2, distance);
    depth = normalize(depth, "OPEN_STABLE");
    setupAdapter = normalize(setupAdapter, "single_block");
    setupWaitTicks = Math.max(1, setupWaitTicks);
    interactionItemId = normalize(interactionItemId, "");
    hitFace = normalize(hitFace, "UP").toUpperCase(java.util.Locale.ROOT);
    hitOffsetX = requireHitOffset(hitOffsetX, "hitOffsetX");
    hitOffsetY = requireHitOffset(hitOffsetY, "hitOffsetY");
    hitOffsetZ = requireHitOffset(hitOffsetZ, "hitOffsetZ");
    expectedMenuRegex = normalize(expectedMenuRegex, "");
    expectedScreenRegex = normalize(expectedScreenRegex, "");
  }

  String setupCommand() {
    String command =
        "rtsbuilding_gui_compat_setup " + id + " " + blockId + " " + distance + " " + setupAdapter;
    return interactionItemId.isBlank() ? command : command + " " + interactionItemId;
  }

  boolean discoveryOnly() {
    return "DISCOVER_THEN_LOCK".equals(expectedMenuRegex)
        || "DISCOVER_THEN_LOCK".equals(expectedScreenRegex);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("GUI compat case requires " + field);
    }
    return value.trim();
  }

  private static String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static double requireHitOffset(double value, String field) {
    if (!Double.isFinite(value) || value < -0.5D || value > 0.5D) {
      throw new IllegalArgumentException(field + " must be finite and within [-0.5, 0.5]");
    }
    return value;
  }
}
