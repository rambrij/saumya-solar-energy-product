package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.math.BigDecimal;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="subscription_plan") @Getter @Setter @NoArgsConstructor
public class SubscriptionPlan {
 @Id UUID id; String planCode; String name; String description; String billingCycle; BigDecimal price; String currency;
 int maxUsers; int maxBranches; int maxWarehouses; long maxStorageMb;
 @Column(columnDefinition="longtext") String entitlementsJson;
 boolean active; Instant createdAt; Instant updatedAt;
}
