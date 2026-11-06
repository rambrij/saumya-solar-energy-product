package com.saumyasolar.domain;
import jakarta.persistence.Column;import jakarta.persistence.Entity;import jakarta.persistence.Id;import jakarta.persistence.Table;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="installation_team") @Getter @Setter @NoArgsConstructor
public class InstallationTeam{@Id UUID id;UUID tenantId;String name;@Column(length=2000) String memberIdsJson;boolean active;Instant createdAt;}