package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.Instant;import java.util.UUID;
@Entity @Table(name="tenant_storage_ledger") @Getter @Setter @NoArgsConstructor
public class TenantStorageLedgerEntry {@Id UUID id;UUID tenantId;String operationId;String objectKey;String objectCategory;long byteDelta;String checksumSha256;Instant recordedAt;}