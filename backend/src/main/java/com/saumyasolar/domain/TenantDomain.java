package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="tenant_domain") @Getter @Setter @NoArgsConstructor
public class TenantDomain {
 @Id UUID id; UUID tenantId; String domain; String verificationToken; String status; Instant createdAt; Instant verifiedAt;
}
