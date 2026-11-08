package com.saumyasolar.repo;
import com.saumyasolar.domain.SubscriptionPlan;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;import java.util.UUID;
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan,UUID> {
 Optional<SubscriptionPlan> findByPlanCodeIgnoreCase(String planCode);
 List<SubscriptionPlan> findByActiveTrueOrderByPriceAsc();
}
