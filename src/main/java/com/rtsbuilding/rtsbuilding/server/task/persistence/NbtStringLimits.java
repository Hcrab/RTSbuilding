package com.rtsbuilding.rtsbuilding.server.task.persistence;

/** Java DataOutput.writeUTF / NBT 字符串使用 Modified UTF-8，长度上限按字节而非字符计算。 */
public final class NbtStringLimits {
    public static final int MAX_MODIFIED_UTF_BYTES = 65_535;

    private NbtStringLimits() {
    }

    public static int requireWritable(String value, String field) {
        int bytes = modifiedUtfBytes(value);
        if (bytes > MAX_MODIFIED_UTF_BYTES) {
            throw new IllegalArgumentException(field + " 的 Modified-UTF 长度超过 65535 字节");
        }
        return bytes;
    }

    public static int modifiedUtfBytes(String value) {
        long bytes = 0L;
        for (int i = 0; i < value.length(); i++) {
            int ch = value.charAt(i);
            bytes += ch >= 0x0001 && ch <= 0x007F ? 1L : ch <= 0x07FF ? 2L : 3L;
            if (bytes > MAX_MODIFIED_UTF_BYTES) return MAX_MODIFIED_UTF_BYTES + 1;
        }
        return (int) bytes;
    }
}
