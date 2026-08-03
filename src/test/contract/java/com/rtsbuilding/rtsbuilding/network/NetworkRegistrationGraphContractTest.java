package com.rtsbuilding.rtsbuilding.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证所有 1.12.2 packet registrar 都能从统一入口到达，且协议编号和消息类型不重复。
 *
 * <p>这项测试不维护一份容易过期的手写 packet 清单，而是直接扫描生产源码并构建 register 调用图。
 * 新增领域注册器后，只要忘记接到根注册器，测试就会指出不可达的类。</p>
 */
class NetworkRegistrationGraphContractTest {
    private static final Path NETWORK_ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/network");
    private static final Pattern CLASS_NAME = Pattern.compile(
            "\\b(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z0-9_]+)");
    private static final Pattern REGISTER_CALL = Pattern.compile(
            "\\b([A-Za-z0-9_]+)\\s*\\.\\s*register\\s*\\(\\s*\\)\\s*;");
    private static final Pattern MESSAGE_REGISTRATION = Pattern.compile(
            "RtsPayloadRegistrar\\s*\\.\\s*registerMessage\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*|\\d+)\\s*,.*?"
                    + "([A-Za-z0-9_]+Payload)\\s*\\.\\s*class\\s*,\\s*Side\\s*\\.\\s*(CLIENT|SERVER)",
            Pattern.DOTALL);
    private static final Pattern INTEGER_CONSTANT = Pattern.compile(
            "\\b(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?(?:final\\s+)?int\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(\\d+)\\s*;");

    @Test
    void everyPacketRegistrarIsReachableAndProtocolKeysAreUnique() throws IOException {
        Map<String, String> sources = readSourcesByClass();
        Set<String> reachable = reachableFrom("RtsPayloadRegistrar", sources);
        Map<Integer, String> discriminatorOwners = new HashMap<>();
        Map<String, String> payloadOwners = new HashMap<>();
        int registrations = 0;

        for (Map.Entry<String, String> source : sources.entrySet()) {
            Map<String, Integer> integerConstants = readIntegerConstants(source.getValue());
            Matcher messages = MESSAGE_REGISTRATION.matcher(source.getValue());
            boolean ownsMessages = false;
            while (messages.find()) {
                ownsMessages = true;
                registrations++;
                int discriminator = resolveDiscriminator(messages.group(1), integerConstants, source.getKey());
                String payload = messages.group(2);
                String owner = source.getKey();

                assertEquals(null, discriminatorOwners.put(discriminator, owner),
                        "packet discriminator " + discriminator + " 被重复注册");
                assertEquals(null, payloadOwners.put(payload, owner),
                        payload + " 被多个 registrar 重复注册");
            }
            if (ownsMessages) {
                assertTrue(reachable.contains(source.getKey()),
                        source.getKey() + " 拥有 packet，但无法从 RtsPayloadRegistrar.register() 到达");
            }
        }

        assertTrue(registrations >= 50, "协议注册数量异常偏低，可能是扫描规则或根接线退化");
    }

    private static Map<String, Integer> readIntegerConstants(String source) {
        Map<String, Integer> constants = new HashMap<>();
        Matcher matcher = INTEGER_CONSTANT.matcher(source);
        while (matcher.find()) {
            constants.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        return constants;
    }

    private static int resolveDiscriminator(String token, Map<String, Integer> constants, String owner) {
        if (Character.isDigit(token.charAt(0))) {
            return Integer.parseInt(token);
        }
        Integer value = constants.get(token);
        assertTrue(value != null, owner + " 的 packet discriminator 常量无法解析：" + token);
        return value;
    }

    private static Map<String, String> readSourcesByClass() throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> files = Files.walk(NETWORK_ROOT)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                Matcher type = CLASS_NAME.matcher(source);
                if (type.find()) {
                    sources.put(type.group(1), source);
                }
            }
        }
        return sources;
    }

    private static Set<String> reachableFrom(String root, Map<String, String> sources) {
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            String owner = pending.removeFirst();
            if (!reachable.add(owner)) continue;
            String source = sources.get(owner);
            if (source == null) continue;
            Matcher calls = REGISTER_CALL.matcher(source);
            while (calls.find()) {
                String target = calls.group(1);
                if (sources.containsKey(target) && !reachable.contains(target)) {
                    pending.addLast(target);
                }
            }
        }
        return reachable;
    }
}
