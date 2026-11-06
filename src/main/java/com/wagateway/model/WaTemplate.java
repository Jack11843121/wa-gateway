package com.wagateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模板本地缓存。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wa_template")
public class WaTemplate extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "language", nullable = false, length = 16)
    private String language;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "template_id", length = 128)
    private String templateId;

    @Column(name = "components", columnDefinition = "MEDIUMTEXT")
    private String components;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}