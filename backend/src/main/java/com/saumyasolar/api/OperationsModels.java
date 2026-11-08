package com.saumyasolar.api;
import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.Instant;import java.util.List;import java.util.UUID;
public final class OperationsModels{private OperationsModels(){}
public record WarehouseInput(@NotBlank String code,@NotBlank String name,String address){}
public record ReceiptInput(@NotNull UUID warehouseId,@NotNull UUID productId,@NotNull @DecimalMin("0.0001") BigDecimal quantity,@NotBlank String reason){}
public record IssueInput(@NotNull UUID warehouseId,@NotNull UUID productId,@NotNull @DecimalMin("0.0001") BigDecimal quantity,@NotBlank String reason){}
public record TeamInput(@NotBlank String name,@NotEmpty List<UUID> memberIds){}
public record ChecklistTemplateInput(@NotBlank String code,@NotBlank String title,boolean mandatory){}
public record DamageInput(@NotNull UUID warehouseId,@NotNull UUID productId,@NotNull @DecimalMin("0.0001") BigDecimal quantity,@NotBlank String reason){}
public record AdjustmentInput(@NotNull UUID warehouseId,@NotNull UUID productId,@NotNull @DecimalMin("0.0001") BigDecimal quantity,@NotNull @Pattern(regexp="INCREASE|DECREASE") String direction,@NotBlank String reason){}
public record TransferInput(@NotNull UUID sourceWarehouseId,@NotNull UUID targetWarehouseId,@NotNull UUID productId,@NotNull @DecimalMin("0.0001") BigDecimal quantity,@NotBlank String reason){}
public record ReservationInput(@NotNull UUID warehouseId,@NotBlank String reason){}
public record CustomerProductOrderInput(@NotNull UUID productId,@NotNull @Min(1) @Max(1000) Integer quantity){}
public record OrderDecisionInput(@NotBlank @Pattern(regexp="ACCEPT|REJECT") String decision,@NotBlank @Size(max=500) String reason){}
public record OrderTransitionInput(@NotBlank String status,@NotBlank String reason){}
public record InstallationInput(@NotNull UUID orderId,@NotBlank String siteAddress,@NotNull Instant scheduledAt,@NotNull UUID assignedEngineerId,@NotNull UUID teamId,String teamName,BigDecimal capacityKw,String notes,UUID surveyId){public InstallationInput(UUID orderId,String siteAddress,Instant scheduledAt,UUID assignedEngineerId,UUID teamId,String teamName,BigDecimal capacityKw,String notes){this(orderId,siteAddress,scheduledAt,assignedEngineerId,teamId,teamName,capacityKw,notes,null);}}
public record RescheduleInput(@NotNull Instant scheduledAt,@NotBlank String reason){}
public record ChecklistUpdateInput(Boolean completed,String evidenceUrl,String notes){}
public record EquipmentInput(@NotNull UUID productId,String serialNumber){}
public record CommissionInput(boolean customerSignedOff,@NotBlank String commissioningNotes,List<EquipmentInput> equipment){}
}