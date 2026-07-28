package com.neobank.module.service;

import com.neobank.module.dto.CreateProductVersionRequest;
import com.neobank.module.dto.ProductVersionCreated;
import com.neobank.module.dto.ProductVersionView;
import com.neobank.module.model.ProductConfig;
import com.neobank.module.repository.ProductConfigRepository;
import java.time.Instant; 
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductConfigService {

    private static final Set<String> VALID_CHANNELS = Set.of("WEB", "MOBILE_APP", "BRANCH", "PHONE");

    private final ProductConfigRepository productConfigs;

    public ProductConfigService(ProductConfigRepository productConfigs) {
        this.productConfigs = productConfigs;
    }

    @Transactional
    public ProductVersionCreated createVersion(CreateProductVersionRequest request) {
        validate(request);

        String productCode = request.productCode();
        Integer nextVersion = productConfigs.findMaxVersion(productCode) + 1;
        String channels = String.join(",", request.channels());

        ProductConfig config = new ProductConfig(
                productCode,
                nextVersion,
                request.minAge(),
                request.limitMin(),
                request.limitMax(),
                request.active(),
                channels,
                null,
                Instant.now()
        );

        productConfigs.save(config);
        return new ProductVersionCreated(nextVersion);
    }

    private void validate(CreateProductVersionRequest request) {
        if (request.limitMin() >= request.limitMax()) {
            throw new IllegalArgumentException("limitMin must be less than limitMax");
        }

        if (request.channels() == null || request.channels().isEmpty()) {
            throw new IllegalArgumentException("channels must not be empty");
        }

        for (String channel : request.channels()) {
            if (channel == null || channel.isBlank()) {
                throw new IllegalArgumentException("channel must not be blank");
            }
            if (!VALID_CHANNELS.contains(channel)) {
                throw new IllegalArgumentException("Unknown channel: " + channel);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ProductVersionView> getVersions(String productCode) {
        List<ProductConfig> all = productConfigs.findByProductCodeOrderByVersionAsc(productCode);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("Unknown productCode: " + productCode);
        }

        Integer maxVersion = all.get(all.size() - 1).getVersion();
        return all.stream()
                .map(config -> ProductVersionView.of(config, config.getVersion().equals(maxVersion)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getAllProductCodes() {
        return productConfigs.findAllProductCodes();
    }

    @Transactional(readOnly = true)
    public ProductVersionView getCurrentVersion(String productCode) {
        return productConfigs.findTopByProductCodeOrderByVersionDesc(productCode)
                .map(config -> ProductVersionView.of(config, true))
                .orElseThrow(() -> new IllegalArgumentException("Unknown productCode: " + productCode));
    }
}