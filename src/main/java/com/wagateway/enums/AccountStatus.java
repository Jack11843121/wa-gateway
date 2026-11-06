package com.wagateway.enums;

import lombok.Getter;

/**
 * 账号状态。
 */
@Getter
public enum AccountStatus {
    ACTIVE(1),
    DISABLED(0);

    private final int value;

    AccountStatus(int value) {
        this.value = value;
    }
}