package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="service_visit") @Getter @Setter @NoArgsConstructor
public class ServiceVisit {@Id UUID id;UUID tenantId;UUID serviceRequestId;UUID engineerId;Instant scheduledAt;Instant startedAt;Instant completedAt;@Column(length=4000) String engineerNotes;@Column(length=2000) String resolution;String evidenceUrl;boolean customerConfirmed;Instant customerConfirmedAt;}
