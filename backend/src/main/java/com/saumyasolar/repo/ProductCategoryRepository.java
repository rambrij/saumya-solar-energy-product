package com.saumyasolar.repo;
import com.saumyasolar.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
 List<ProductCategory> findByTenantIdOrderByIdDesc(UUID tenantId);
 Optional<ProductCategory> findByIdAndTenantId(UUID id, UUID tenantId);
 Optional<ProductCategory> findByNameAndTenantId(String name, UUID tenantId);
}