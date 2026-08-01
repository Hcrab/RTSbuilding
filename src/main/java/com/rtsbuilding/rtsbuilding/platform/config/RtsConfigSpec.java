package com.rtsbuilding.rtsbuilding.platform.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 加载器无关的轻量配置规格与 JSON 持久化实现。
 *
 * <p>该类只负责声明、校验、读取和原子保存配置值，不负责创建设置界面，也不把服务端配置
 * 同步给客户端。字段 API 刻意贴近旧版规格，便于业务层保持稳定；Fabric 入口负责为三个规格
 * 指定实际文件路径并决定加载时机。
 */
public final class RtsConfigSpec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Value<?>> values;
    private Path path;

    private RtsConfigSpec(List<Value<?>> entries) {
        Map<String, Value<?>> collected = new LinkedHashMap<>();
        for (Value<?> entry : entries) {
            if (collected.put(entry.key, entry) != null) {
                throw new IllegalStateException("重复的 RTS 配置键: " + entry.key);
            }
            entry.owner = this;
        }
        this.values = Map.copyOf(collected);
    }

    /**
     * 从指定文件加载配置；缺失文件会使用当前默认值创建。
     */
    public synchronized void load(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        boolean rewrite = !Files.isRegularFile(this.path);
        if (!rewrite) {
            try (Reader reader = Files.newBufferedReader(this.path)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    throw new IOException("配置根节点不是 JSON 对象");
                }
                JsonObject object = root.getAsJsonObject();
                for (Value<?> value : this.values.values()) {
                    JsonElement encoded = object.get(value.key);
                    if (encoded == null || encoded.isJsonNull() || !value.read(encoded)) {
                        rewrite = true;
                    }
                }
            } catch (RuntimeException | IOException failure) {
                backupBrokenFile();
                resetDefaults();
                rewrite = true;
            }
        }
        if (rewrite) {
            save();
        }
    }

    /**
     * 原子保存当前值；若文件尚未绑定路径则保持内存值，不制造错误文件。
     */
    public synchronized void save() {
        if (this.path == null) {
            return;
        }
        try {
            Files.createDirectories(this.path.getParent());
            JsonObject object = new JsonObject();
            for (Value<?> value : this.values.values()) {
                object.add(value.key, value.write());
            }
            Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(object, writer);
            }
            try {
                Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("无法保存 RTS 配置: " + this.path, failure);
        }
    }

    private void resetDefaults() {
        this.values.values().forEach(Value::reset);
    }

    private void backupBrokenFile() {
        if (this.path == null || !Files.isRegularFile(this.path)) {
            return;
        }
        Path backup = this.path.resolveSibling(this.path.getFileName() + ".broken-" + Instant.now().toEpochMilli());
        try {
            Files.move(this.path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 保存新文件时仍会尝试覆盖原路径；备份失败不应阻止游戏启动。
        }
    }

    public static final class Builder {
        private final List<Value<?>> values = new ArrayList<>();

        public Builder comment(String ignored) {
            return this;
        }

        public Builder translation(String ignored) {
            return this;
        }

        public BooleanValue define(String key, boolean defaultValue) {
            return add(new BooleanValue(key, defaultValue));
        }

        public IntValue defineInRange(String key, int defaultValue, int minimum, int maximum) {
            return add(new IntValue(key, defaultValue, minimum, maximum));
        }

        public LongValue defineInRange(String key, long defaultValue, long minimum, long maximum) {
            return add(new LongValue(key, defaultValue, minimum, maximum));
        }

        public DoubleValue defineInRange(String key, double defaultValue, double minimum, double maximum) {
            return add(new DoubleValue(key, defaultValue, minimum, maximum));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String key, E defaultValue) {
            return add(new EnumValue<>(key, defaultValue));
        }

        public RtsConfigSpec build() {
            return new RtsConfigSpec(this.values);
        }

        private <V extends Value<?>> V add(V value) {
            this.values.add(value);
            return value;
        }
    }

    public abstract static class Value<T> {
        private final String key;
        private final T defaultValue;
        private final Function<JsonElement, T> decoder;
        private final Function<T, JsonElement> encoder;
        private final UnaryOperator<T> normalizer;
        private volatile T value;
        private RtsConfigSpec owner;

        private Value(String key, T defaultValue, Function<JsonElement, T> decoder,
                Function<T, JsonElement> encoder, UnaryOperator<T> normalizer) {
            this.key = Objects.requireNonNull(key, "key");
            this.defaultValue = defaultValue;
            this.decoder = decoder;
            this.encoder = encoder;
            this.normalizer = normalizer;
            this.value = normalizer.apply(defaultValue);
        }

        public T get() {
            return this.value;
        }

        public void set(T value) {
            this.value = this.normalizer.apply(Objects.requireNonNull(value, "value"));
        }

        private boolean read(JsonElement element) {
            try {
                T decoded = this.decoder.apply(element);
                T normalized = this.normalizer.apply(decoded);
                this.value = normalized;
                return Objects.equals(decoded, normalized);
            } catch (RuntimeException failure) {
                reset();
                return false;
            }
        }

        private JsonElement write() {
            return this.encoder.apply(this.value);
        }

        private void reset() {
            this.value = this.normalizer.apply(this.defaultValue);
        }
    }

    public static final class BooleanValue extends Value<Boolean> {
        private BooleanValue(String key, boolean defaultValue) {
            super(key, defaultValue, JsonElement::getAsBoolean,
                    value -> GSON.toJsonTree(value), UnaryOperator.identity());
        }

        public boolean getAsBoolean() {
            return get();
        }
    }

    public static final class IntValue extends Value<Integer> {
        private IntValue(String key, int defaultValue, int minimum, int maximum) {
            super(key, defaultValue, JsonElement::getAsInt, value -> GSON.toJsonTree(value),
                    value -> Math.max(minimum, Math.min(maximum, value)));
        }

        public int getAsInt() {
            return get();
        }
    }

    public static final class LongValue extends Value<Long> {
        private LongValue(String key, long defaultValue, long minimum, long maximum) {
            super(key, defaultValue, JsonElement::getAsLong, value -> GSON.toJsonTree(value),
                    value -> Math.max(minimum, Math.min(maximum, value)));
        }

        public long getAsLong() {
            return get();
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private DoubleValue(String key, double defaultValue, double minimum, double maximum) {
            super(key, defaultValue, JsonElement::getAsDouble, value -> GSON.toJsonTree(value),
                    value -> Math.max(minimum, Math.min(maximum, value)));
        }

        public double getAsDouble() {
            return get();
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends Value<E> {
        private EnumValue(String key, E defaultValue) {
            super(key, defaultValue,
                    element -> Enum.valueOf(defaultValue.getDeclaringClass(), element.getAsString()),
                    value -> GSON.toJsonTree(value.name()), UnaryOperator.identity());
        }
    }
}
