package com.rtsbuilding.rtsbuilding.test;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 给文本行为测试加载实际随包发布的翻译；不启动资源重载或游戏客户端。 */
public final class ModTranslationsFixture implements BeforeAllCallback, AfterAllCallback {
    private Language original;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        original = Language.getInstance();
        Map<String, String> translations = new HashMap<>();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/rtsbuilding/lang/en_us.json"))) {
            Language.loadFromJson(input, translations::put);
        }
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return translations.getOrDefault(key, original.getOrDefault(key, fallback));
            }

            @Override
            public boolean has(String key) {
                return translations.containsKey(key) || original.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return original.isDefaultRightToLeft();
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return original.getVisualOrder(text);
            }
        });
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Language.inject(original);
    }
}
