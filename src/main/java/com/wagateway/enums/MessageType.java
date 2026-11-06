package com.wagateway.enums;

import lombok.Getter;

/**
 * 消息类型：对应 Meta Cloud API 的 messages/type 字段。
 */
@Getter
public enum MessageType {
    TEXT("text"),
    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video"),
    DOCUMENT("document"),
    STICKER("sticker"),
    LOCATION("location"),
    CONTACTS("contacts"),
    INTERACTIVE("interactive"),
    TEMPLATE("template"),
    REACTION("reaction");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }
}