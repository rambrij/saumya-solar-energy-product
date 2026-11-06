package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "sales_lead")
@Getter
@Setter
@NoArgsConstructor
public class SalesLead {
    @Id
    UUID id;
    UUID tenantId;
    UUID enquiryId;
    UUID customerId;
    String stage;
    String priority;
    UUID assignedUserId;
    Instant nextFollowUp;
    String lostReason;
    Instant createdAt;
}
