package com.rtsbuilding.rtsbuilding.network;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * 1.12.2 数据包的公共 ByteBuf 约定。
 *
 * <p>固定宽度数值沿用 Netty 大端序；可变整数沿用 Minecraft VarInt，最多五字节。
 * 集合和字符串在读取前必须先经过本类的上限检查，避免恶意长度造成内存分配。</p>
 */
public final class RtsPacketBuffer {
    private RtsPacketBuffer() {
    }

    public static void writeVarInt(ByteBuf buffer, int value) {
        while ((value & -128) != 0) {
            buffer.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }

    public static int readVarInt(ByteBuf buffer) {
        int result = 0;
        int bytes = 0;
        byte current;
        do {
            if (bytes == 5) {
                throw new IllegalArgumentException("VarInt exceeds five bytes");
            }
            current = buffer.readByte();
            result |= (current & 127) << (bytes++ * 7);
        } while ((current & 128) != 0);
        return result;
    }

    public static int readBoundedCount(ByteBuf buffer, int maximum, String fieldName) {
        int count = readVarInt(buffer);
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(fieldName + " out of range: " + count);
        }
        return count;
    }

    public static void writeUuid(ByteBuf buffer, UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID must not be null");
        }
        buffer.writeLong(value.getMostSignificantBits());
        buffer.writeLong(value.getLeastSignificantBits());
    }

    public static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    public static void writeByteArray(ByteBuf buffer, byte[] value, int maximum, String fieldName) {
        byte[] safeValue = value == null ? new byte[0] : value;
        if (safeValue.length > maximum) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maximum + " bytes");
        }
        writeVarInt(buffer, safeValue.length);
        buffer.writeBytes(safeValue);
    }

    public static byte[] readByteArray(ByteBuf buffer, int maximum, String fieldName) {
        int length = readBoundedCount(buffer, maximum, fieldName);
        byte[] value = new byte[length];
        buffer.readBytes(value);
        return value;
    }

    public static void writeString(ByteBuf buffer, String value, int maximumChars, String fieldName) {
        String safeValue = value == null ? "" : value;
        if (safeValue.length() > maximumChars) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maximumChars + " characters");
        }
        byte[] bytes = safeValue.getBytes(StandardCharsets.UTF_8);
        int maximumBytes = maximumUtf8Bytes(maximumChars);
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException(fieldName + " UTF-8 data is too long");
        }
        writeVarInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }

    public static String readString(ByteBuf buffer, int maximumChars, String fieldName) {
        int length = readBoundedCount(buffer, maximumUtf8Bytes(maximumChars), fieldName + " bytes");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        final String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(fieldName + " is not valid UTF-8", exception);
        }
        if (value.length() > maximumChars) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maximumChars + " characters");
        }
        return value;
    }

    private static int maximumUtf8Bytes(int maximumChars) {
        if (maximumChars < 0 || maximumChars > Integer.MAX_VALUE / 4) {
            throw new IllegalArgumentException("Invalid character limit: " + maximumChars);
        }
        return maximumChars * 4;
    }

    /** 使用 Forge/Minecraft 原生格式保留物品、damage 和完整 NBT；原型只作匹配提示。 */
    public static void writeItemStack(ByteBuf buffer, ItemStack stack) {
        ByteBufUtils.writeItemStack(buffer, stack == null ? null : stack);
    }

    public static ItemStack readItemStack(ByteBuf buffer) {
        ItemStack stack = ByteBufUtils.readItemStack(buffer);
        return stack == null ? null : stack;
    }
}
