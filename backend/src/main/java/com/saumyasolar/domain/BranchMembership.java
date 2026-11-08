package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="branch_user") @Getter @Setter @NoArgsConstructor
public class BranchMembership {
 @Id UUID id; UUID tenantId; UUID branchId; UUID userId; Instant createdAt;
}
