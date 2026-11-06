package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Enquiry {
    @Id
    UUID id;
    UUID tenantId;
    UUID customerId;
    String name;
    String email;
    String phone;
    String propertyType;
    BigDecimal monthlyBill;
    BigDecimal requiredCapacityKw;
    @Column(columnDefinition = "TEXT")
    String message;
    String status;
    Instant createdAt;
}
