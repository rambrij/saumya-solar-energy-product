package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.math.BigDecimal;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Tenant {
    @Id
    UUID id;
    String name;
    String slug;
    String status;
    BigDecimal discountApprovalThreshold = BigDecimal.TEN;
}
