package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CustomerProfile {
    @Id
    UUID id;
    UUID tenantId;
    UUID userId;
    String name;
    String email;
    String phone;
    String address;
    String gstin;
    boolean smsConsent = false;
    boolean whatsappConsent = false;
    java.time.Instant messagingConsentUpdatedAt;
    boolean active = true;
}
