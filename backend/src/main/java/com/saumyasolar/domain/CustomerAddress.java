package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="customer_address")
@Getter @Setter @NoArgsConstructor
public class CustomerAddress {
 @Id private UUID id;
 @Column(nullable=false) private UUID tenantId;
 @Column(nullable=false) private UUID customerId;
 @Column(nullable=false,length=80) private String label;
 @Column(nullable=false,length=180) private String line1;
 @Column(length=180) private String line2;
 @Column(nullable=false,length=100) private String city;
 @Column(nullable=false,length=100) private String state;
 @Column(nullable=false,length=12) private String pincode;
 @Column(nullable=false,length=80) private String country;
 @Column(length=120) private String landmark;
 @Column(nullable=false) private boolean isDefault;
 @Column(nullable=false) private Instant createdAt;
 @Column(nullable=false) private Instant updatedAt;
}
