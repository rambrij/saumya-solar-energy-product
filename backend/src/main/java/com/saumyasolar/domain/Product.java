package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class Product {
    @Id
    UUID id;
    UUID tenantId;
    UUID categoryId;
    String sku;
    String name;
    @Column(columnDefinition = "TEXT")
    String description;
    BigDecimal price;
    BigDecimal taxPercent;
    boolean active;
    @Column(columnDefinition = "JSON")
    String attributesJson;
}
