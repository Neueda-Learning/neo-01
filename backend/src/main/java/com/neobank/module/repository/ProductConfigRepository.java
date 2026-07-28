package com.neobank.module.repository;

import com.neobank.module.model.ProductConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductConfigRepository extends JpaRepository<ProductConfig, Long> {

    /** The active (highest-version) config for a product code. */
    Optional<ProductConfig> findTopByProductCodeOrderByVersionDesc(String productCode);

    /** All versions for a product code, ordered oldest first. */
    List<ProductConfig> findByProductCodeOrderByVersionAsc(String productCode);

    /** The highest version number for a product code, or 0 if none exist. */
    @Query("SELECT COALESCE(MAX(p.version), 0) FROM ProductConfig p WHERE p.productCode = :code")
    Integer findMaxVersion(@Param("code") String productCode);

    /** All product codes that have at least one version. */
    @Query("SELECT DISTINCT p.productCode FROM ProductConfig p ORDER BY p.productCode")
    List<String> findAllProductCodes();
}