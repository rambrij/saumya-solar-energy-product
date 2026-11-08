package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="service_comment") @Getter @Setter @NoArgsConstructor
public class ServiceComment {
 @Id UUID id; UUID tenantId; UUID serviceRequestId; UUID authorId;
 @Column(nullable=false,length=20) String visibility;
 @Column(nullable=false,length=4000) String body;
 Instant createdAt;
}