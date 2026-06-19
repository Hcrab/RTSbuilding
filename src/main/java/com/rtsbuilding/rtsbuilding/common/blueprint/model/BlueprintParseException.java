package com.rtsbuilding.rtsbuilding.common.blueprint.model;

/**
 * 蓝图解析异常 —— 在读取或解析蓝图文件时抛出。
 * <p>
 * 封装了文件格式错误、数据损坏或不兼容等解析失败场景。
 */
public final class BlueprintParseException extends Exception {

    /**
     * 使用指定的错误消息构造异常。
     *
     * @param message 描述解析失败原因的消息
     */
    public BlueprintParseException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误消息和根本原因构造异常。
     *
     * @param message 描述解析失败原因的消息
     * @param cause   导致解析失败的底层异常
     */
    public BlueprintParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
