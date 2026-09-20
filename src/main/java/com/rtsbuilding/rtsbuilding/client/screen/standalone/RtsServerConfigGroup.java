package com.rtsbuilding.rtsbuilding.client.screen.standalone;

/** 世界规则的玩家可理解分组顺序；组内字段仍由 RtsServerConfigField 白名单决定。 */
enum RtsServerConfigGroup {
    RULES("rules"), HOME("home"), BUILDING("building"), MINING("mining"),
    BLUEPRINT("blueprint"), STORAGE("storage"), WORKFLOWS("workflows"), HISTORY("history");

    final String translationId;

    RtsServerConfigGroup(String translationId) {
        this.translationId = translationId;
    }

    String titleKey() {
        return "screen.rtsbuilding.world.group." + translationId;
    }

    String hintKey() {
        return titleKey() + ".hint";
    }
}
