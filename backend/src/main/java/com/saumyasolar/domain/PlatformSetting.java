package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_setting")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSetting {
    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "value_text", nullable = false, length = 500)
    private String value;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "updated_by", nullable = false)
    private java.util.UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
