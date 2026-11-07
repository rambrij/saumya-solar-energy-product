package com.saumyasolar.domain;
import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import java.util.UUID;
@Entity @Table(name="invoice_sequence",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","fiscal_year"})) @Getter @Setter @NoArgsConstructor
public class InvoiceSequence {@Id UUID id;UUID tenantId;String fiscalYear;long nextNumber;}
