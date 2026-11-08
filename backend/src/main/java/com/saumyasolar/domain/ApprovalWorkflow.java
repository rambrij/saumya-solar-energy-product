package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="approval_workflow") @Getter @Setter @NoArgsConstructor
public class ApprovalWorkflow {
 @Id UUID id; UUID tenantId; String code; String name; String entityType;
 @Column(columnDefinition="longtext") String stepsJson; boolean active; Instant createdAt; Instant updatedAt;
}
