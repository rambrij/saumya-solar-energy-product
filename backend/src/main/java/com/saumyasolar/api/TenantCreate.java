package com.saumyasolar.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantCreate(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 120) String slug,
        @NotBlank @Size(max = 150) String adminName,
        @NotBlank @Email @Size(max = 190) String adminEmail
) {}
