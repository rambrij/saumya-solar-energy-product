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
    @Column(nullable = false)
    UUID categoryId;
    String sku;
    String name;
    String manufacturer;
    String model;
    String technology;
    BigDecimal purchasePrice;
    BigDecimal discountPercent;
    Integer warrantyYears;
    String imageUrl;
    String datasheetUrl;
    BigDecimal reorderLevel;
    String unitOfMeasure;
    String hsnCode;
    boolean serialized;
    @Column(columnDefinition = "TEXT")
    String description;
    BigDecimal price;
    BigDecimal taxPercent;
    boolean active;
    @Column(columnDefinition = "JSON")
    String attributesJson;
}
