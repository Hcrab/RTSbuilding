package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.server.network.RtsAreaDestroyFragmentReassembler;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 在内存缓冲区验证真实协议，不启动游戏进程或世界。 */
class MiningFragmentWireTest {
    private static final boolean[] originalSync = new boolean[2];

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 普通 JUnit 不派发 NeoForge 的注册表事件；只补齐真实 ItemStack wire codec 所需
        // 的同步标记，不启动 ModLoader/世界，并在本类结束后恢复，避免污染其它用例。
        var setter = net.neoforged.neoforge.registries.BaseMappedRegistry.class
                .getDeclaredMethod("setSync", boolean.class);
        setter.setAccessible(true);
        var registries = List.of(BuiltInRegistries.ITEM, BuiltInRegistries.DATA_COMPONENT_TYPE);
        for (int i = 0; i < registries.size(); i++) {
            originalSync[i] = registries.get(i).doesSync();
            setter.invoke(registries.get(i), true);
        }
    }

    @AfterAll
    static void restoreRegistrySync() throws ReflectiveOperationException {
        var setter = net.neoforged.neoforge.registries.BaseMappedRegistry.class
                .getDeclaredMethod("setSync", boolean.class);
        setter.setAccessible(true);
        setter.invoke(BuiltInRegistries.ITEM, originalSync[0]);
        setter.invoke(BuiltInRegistries.DATA_COMPONENT_TYPE, originalSync[1]);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    @Test
    void maximumSelectionSurvivesRealWireCodecAndCreatesOneCompleteRequest() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) for (int z = 0; z < 64; z++) {
            positions.add(new BlockPos(x, y, z));
        }
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.setDamageValue(17);
        CompoundTag custom = new CompoundTag();
        custom.putString("owner_note", "保留模组工具数据");
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var fragments = C2SRtsAreaDestroyFragmentPayload.split(1, 2, 3, 4, 0,
                (byte) 1, (byte) 0, positions, (byte) 2, "minecraft:diamond_pickaxe", tool, true);
        var reassembler = new RtsAreaDestroyFragmentReassembler();
        int completions = 0;
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            C2SRtsAreaDestroyFragmentPayload.validateTransfer(fragments, buffer.registryAccess());
            for (var fragment : fragments.reversed()) {
                buffer.clear();
                C2SRtsAreaDestroyFragmentPayload.STREAM_CODEC.encode(buffer, fragment);
                assertTrue(buffer.readableBytes() <= C2SRtsAreaDestroyFragmentPayload.MAX_FRAGMENT_BYTES);
                var decoded = C2SRtsAreaDestroyFragmentPayload.STREAM_CODEC.decode(buffer);
                assertEquals(0, buffer.readableBytes());
                var result = reassembler.accept(decoded, 10);
                if (result.status() == RtsAreaDestroyFragmentReassembler.Status.COMPLETE) {
                    completions++;
                    assertEquals(positions, result.payload().positions());
                    assertTrue(ItemStack.matches(tool, result.payload().toolPrototype()));
                    assertTrue(result.payload().toolProtectionEnabled());
                }
            }
            assertEquals(1, completions);
        } finally {
            buffer.release();
        }
    }

    @Test
    void oversizeLegacyRequestFailsInsteadOfTruncating() {
        var positions = java.util.stream.IntStream.range(0, 5000).mapToObj(x -> new BlockPos(x, 0, 0)).toList();
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            assertThrows(IllegalArgumentException.class, () -> C2SRtsAreaDestroyPayload.STREAM_CODEC.encode(
                    buffer, new C2SRtsAreaDestroyPayload(positions, (byte) 0, "", ItemStack.EMPTY, false)));
            buffer.clear();
            buffer.writeVarInt(262144);
            assertThrows(IllegalArgumentException.class, () -> C2SRtsAreaDestroyPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void oversizedToolIsRejectedBeforeAnyFragmentIsSent() {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        CompoundTag custom = new CompoundTag();
        custom.putByteArray("bulk", new byte[30000]);
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var fragments = C2SRtsAreaDestroyFragmentPayload.split(1, 2, 3, 4, 0,
                (byte) 1, (byte) 0, List.of(BlockPos.ZERO), (byte) 0, "", tool, false);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> C2SRtsAreaDestroyFragmentPayload.validateTransfer(fragments, buffer.registryAccess()));
        } finally {
            buffer.release();
        }
    }
}
