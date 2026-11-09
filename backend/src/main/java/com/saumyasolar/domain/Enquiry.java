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
    UUID branchId;
    UUID customerId;
    UUID addressId;
    @Column(name = "site_address", length = 500)
    String siteAddress;
    @Column(name = "city_pincode", length = 160)
    String cityPincode;
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
