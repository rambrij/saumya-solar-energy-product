package com.saumyasolar.api;
import jakarta.validation.constraints.NotNull;
public record CustomerMessagingConsent(@NotNull Boolean smsConsent,@NotNull Boolean whatsappConsent) {}