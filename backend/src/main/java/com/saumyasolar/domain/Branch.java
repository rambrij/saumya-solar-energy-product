package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
public class Branch {
    @Id
    private UUID id;
    private UUID tenantId;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
