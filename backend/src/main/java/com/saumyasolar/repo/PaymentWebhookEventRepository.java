package com.saumyasolar.repo;

import com.saumyasolar.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.util.UUID;
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByProviderAndEventId(String provider, String eventId);
}
