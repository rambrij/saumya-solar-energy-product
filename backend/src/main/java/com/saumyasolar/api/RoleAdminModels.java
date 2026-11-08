package com.saumyasolar.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class RoleAdminModels {
    private RoleAdminModels() {}
    public record RoleInput(@NotBlank @Size(min=3,max=40) String name,
                            @NotEmpty Set<@NotBlank String> permissionCodes) {}
}
