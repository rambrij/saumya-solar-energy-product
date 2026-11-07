package com.saumyasolar.domain;
import jakarta.persistence.Entity;import jakarta.persistence.Id;import jakarta.persistence.Table;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="warehouse") @Getter @Setter @NoArgsConstructor
public class Warehouse {@Id UUID id;UUID tenantId;String code;String name;String address;boolean active;Instant createdAt;}