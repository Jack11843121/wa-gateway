package com.wagateway.enums;

import lombok.Getter;

/**
 * 消息方向与投递状态。
 */
public final class MessageStatuses {

    private MessageStatuses() {
    }

    /**
     * 出站消息的网关侧状态（在 Meta 状态基础上扩展了队列态）。
     */
    @Getter
    public enum Outbound {
        QUEUED("queued"),
        SENT("sent"),
        DELIVERED("delivered"),
        READ("read"),
        FAILED("failed"),
        DELETED("deleted");

        private final String value;

        Outbound(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum Direction {
        OUTBOUND("outbound"),
        INBOUND("inbound");

        private final String value;

        Direction(String value) {
            this.value = value;
        }
    }
}