package com.neobank.module.repository;

import com.neobank.module.model.ProductConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductConfigRepository extends JpaRepository<ProductConfig, Long> {

    /**
     * The active (highest-version) config for a product code.
     * Returns empty when the product is not in the catalogue.
     */
    @Query("SELECT p FROM ProductConfig p WHERE p.productCode = :code ORDER BY p.version DESC LIMIT 1")
    Optional<ProductConfig> findCurrentByProductCode(@Param("code") String productCode);
}
