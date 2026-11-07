package com.saumyasolar.domain;

import jakarta.persistence.Column;\nimport jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SolarPackage {
    @Id
    UUID id;
    UUID tenantId;
    String code;
    String name;
    BigDecimal capacityKw;
    String packageType;
    BigDecimal basePrice;
    Integer warrantyYears;\n    @Column(name = "image_url", length = 1024)\n    String imageUrl;
    boolean active;
}
