package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="approval_decision") @Getter @Setter @NoArgsConstructor
public class ApprovalDecision {
 @Id UUID id; UUID tenantId; UUID instanceId; int stepOrder; String decision; UUID actorId; String comments; Instant createdAt;
}
