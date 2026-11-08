package com.saumyasolar.domain;
import jakarta.persistence.*;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="branch") @Getter @Setter @NoArgsConstructor
public class Branch {
 @Id UUID id; UUID tenantId; String code; String name; String address; String phone; String email;
 boolean active; Instant createdAt; Instant updatedAt;
}
