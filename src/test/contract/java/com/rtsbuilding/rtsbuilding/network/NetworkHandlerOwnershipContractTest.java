package com.rtsbuilding.rtsbuilding.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 锁定 camera/craft C2S 网络入口的唯一所有权。
 *
 * <p>这个测试不依赖 Handler 的具体业务语句，而是从生产源码中解析注册结构：每个 C2S
 * Payload 必须恰好由对应领域 Registrar 注册一次，并且领域内只能保留一个约定位置的
 * Handler。这样以后移动方法实现时不会误报，但复制出第二套入口会立即失败。</p>
 */
class NetworkHandlerOwnershipContractTest {
    private static final Path NETWORK_ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/network");
    private static final Path ROOT_REGISTRAR = NETWORK_ROOT.resolve("RtsPayloadRegistrar.java");
    private static final Pattern C2S_REGISTRATION = Pattern.compile(
            "registrar\\s*\\.\\s*playToServer\\s*\\(\\s*"
                    + "(C2S[A-Za-z0-9_]+Payload)\\s*\\.\\s*TYPE\\s*,\\s*"
                    + "\\1\\s*\\.\\s*STREAM_CODEC\\s*,\\s*"
                    + "([A-Za-z0-9_]+)\\s*::\\s*([A-Za-z0-9_]+)\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+([A-Za-z0-9_.*]+)\\s*;");

    private static final List<DomainContract> DOMAINS = List.of(
            new DomainContract(
                    "camera",
                    "RtsCameraPackets",
                    "RtsCameraNetworkHandlers",
                    orderedMap(
                            "C2SRtsToggleCameraPayload", "handleToggle",
                            "C2SRtsCameraMovePayload", "handleMove")),
            new DomainContract(
                    "craft",
                    "RtsCraftPackets",
                    "RtsCraftNetworkHandlers",
                    orderedMap(
                            "C2SRtsRequestCraftablesPayload", "handleRequestCraftables",
                            "C2SRtsOpenCraftTerminalPayload", "handleOpenCraftTerminal",
                            "C2SRtsCraftRefillPayload", "handleCraftRefill",
                            "C2SRtsCraftRecipePayload", "handleCraftRecipe",
                            "C2SRtsJeiTransferPayload", "handleJeiTransfer")));

    @Test
    void everyCameraAndCraftC2sPayloadHasOneProductionRegistration() throws IOException {
        Map<String, List<Registration>> allRegistrations = readAllC2sRegistrations();

        for (DomainContract domain : DOMAINS) {
            Path registrar = domain.registrarPath();
            Map<String, String> domainRegistrations = registrationsIn(registrar);
            Set<String> payloadFiles = c2sPayloadsIn(domain.domainPath());

            assertEquals(payloadFiles, domainRegistrations.keySet(),
                    domain.name() + " 的每个 C2S Payload 都必须由领域 Registrar 完整注册");
            assertEquals(domain.expectedHandlers(), domainRegistrations,
                    domain.name() + " 的 Payload 必须指向约定的唯一 Handler 方法");

            for (String payload : payloadFiles) {
                List<Registration> registrations = allRegistrations.getOrDefault(payload, List.of());
                assertEquals(1, registrations.size(), payload + " 在生产网络树中必须且只能注册一次");
                Registration registration = registrations.getFirst();
                assertEquals(registrar.normalize(), registration.source().normalize(),
                        payload + " 不得绕开领域 Registrar 从其他入口重复注册");
                assertEquals(domain.handlerClass(), registration.handlerClass(),
                        payload + " 必须交给约定的领域 Handler，不能改接到另一套实现");
            }
        }
    }

    @Test
    void eachDomainKeepsOneHandlerAtTheCanonicalBoundary() throws IOException {
        for (DomainContract domain : DOMAINS) {
            List<Path> handlerCopies;
            try (Stream<Path> files = Files.walk(domain.domainPath())) {
                handlerCopies = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals(domain.handlerClass() + ".java"))
                        .map(Path::normalize)
                        .toList();
            }

            assertEquals(List.of(domain.handlerPath().normalize()), handlerCopies,
                    domain.name() + " 只能保留 handler/ 下的一套生产 Handler");
        }
    }

    @Test
    void rootRegistrarOwnsEachDomainExactlyOnce() throws IOException {
        String source = Files.readString(ROOT_REGISTRAR);
        for (DomainContract domain : DOMAINS) {
            Pattern call = Pattern.compile("\\b" + Pattern.quote(domain.registrarClass())
                    + "\\s*\\.\\s*register\\s*\\(\\s*registrar\\s*\\)");
            assertEquals(1, count(call.matcher(source)),
                    domain.registrarClass() + " 必须由主 Registrar 恰好接入一次");
        }
    }

    @Test
    void registrarsAndServerHandlersDoNotImportClientOnlyClasses() throws IOException {
        for (DomainContract domain : DOMAINS) {
            for (Path productionEntry : List.of(domain.registrarPath(), domain.handlerPath())) {
                Matcher imports = IMPORT.matcher(Files.readString(productionEntry));
                while (imports.find()) {
                    String importedType = imports.group(1);
                    assertFalse(importedType.startsWith("net.minecraft.client.")
                                    || importedType.contains(".client."),
                            productionEntry + " 是公共端/服务端入口，不能直接加载客户端类：" + importedType);
                }
            }
        }
    }

    private static Map<String, List<Registration>> readAllC2sRegistrations() throws IOException {
        Map<String, List<Registration>> registrations = new HashMap<>();
        try (Stream<Path> files = Files.walk(NETWORK_ROOT)) {
            for (Path source : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                Matcher matcher = C2S_REGISTRATION.matcher(Files.readString(source));
                while (matcher.find()) {
                    registrations.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>())
                            .add(new Registration(source, matcher.group(2), matcher.group(3)));
                }
            }
        }
        return registrations;
    }

    private static Map<String, String> registrationsIn(Path source) throws IOException {
        Map<String, String> registrations = new LinkedHashMap<>();
        Matcher matcher = C2S_REGISTRATION.matcher(Files.readString(source));
        while (matcher.find()) {
            String previous = registrations.put(matcher.group(1), matcher.group(3));
            if (previous != null) {
                throw new AssertionError(matcher.group(1) + " 在 " + source + " 中重复注册");
            }
        }
        return registrations;
    }

    private static Set<String> c2sPayloadsIn(Path domainPath) throws IOException {
        try (Stream<Path> files = Files.list(domainPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("C2S") && name.endsWith("Payload.java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private static int count(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static Map<String, String> orderedMap(String... keyValues) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put(keyValues[i], keyValues[i + 1]);
        }
        return Map.copyOf(values);
    }

    private record Registration(Path source, String handlerClass, String handlerMethod) {
    }

    private record DomainContract(
            String name,
            String registrarClass,
            String handlerClass,
            Map<String, String> expectedHandlers) {
        private Path domainPath() {
            return NETWORK_ROOT.resolve(name);
        }

        private Path registrarPath() {
            return domainPath().resolve(registrarClass + ".java");
        }

        private Path handlerPath() {
            return domainPath().resolve("handler").resolve(handlerClass + ".java");
        }
    }
}
