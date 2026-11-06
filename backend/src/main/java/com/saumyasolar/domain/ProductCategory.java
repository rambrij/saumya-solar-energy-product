package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProductCategory {
    @Id
    UUID id;
    UUID tenantId;
    String name;
    String description;
    boolean active;
}
