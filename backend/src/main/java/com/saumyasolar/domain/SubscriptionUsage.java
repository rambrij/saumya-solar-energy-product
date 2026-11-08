package com.saumyasolar.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="subscription_usage") @Getter @Setter @NoArgsConstructor
public class SubscriptionUsage {
 @Id UUID id;
 UUID tenantId;
 String meterCode;
 LocalDate periodStart;
 LocalDate periodEnd;
 long quantity;
 Instant updatedAt;
}
