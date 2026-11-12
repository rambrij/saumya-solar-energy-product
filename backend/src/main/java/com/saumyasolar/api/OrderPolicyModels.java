package com.saumyasolar.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class OrderPolicyModels {
    private OrderPolicyModels() {}
    public record TransitionOption(String fromStatus,String toStatus,String requiredPermission) {}
    public record PolicyRule(String fromStatus,String toStatus,String requiredPermission,boolean enabled) {}
    public record TransitionPolicyUpdate(@NotEmpty List<@Valid PolicyToggle> transitions) {}
    public record PolicyToggle(
            @NotBlank @Size(max=40) String fromStatus,
            @NotBlank @Size(max=40) String toStatus,
            boolean enabled
    ) {}
}
