package com.saumyasolar.repo;
import com.saumyasolar.domain.IntegrationWebhookEvent;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;import java.util.UUID;
public interface IntegrationWebhookEventRepository extends JpaRepository<IntegrationWebhookEvent,UUID>{
 Optional<IntegrationWebhookEvent> findByProviderCodeAndProviderEventId(String providerCode,String providerEventId);
}
