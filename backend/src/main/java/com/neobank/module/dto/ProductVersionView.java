package com.neobank.module.dto;

import com.neobank.module.model.ProductConfig;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * What {@code GET /products/{code}/versions} returns — the operator's view of a product version.
 *
 * <p>Contains all fields from the product_config row plus a derived {@code current} flag that
 * indicates whether this is the highest version (and therefore the one applied to new applications).
 */
public record ProductVersionView(
        String productCode,
        Integer version,
        Integer minAge,
        Integer limitMin,
        Integer limitMax,
        Boolean active,
        List<String> channels,
        Instant effectiveFrom,
        Boolean current) {

    public static ProductVersionView of(ProductConfig config, boolean current) {
        List<String> channelsList = config.getChannels() != null
                ? Arrays.asList(config.getChannels().split(","))
                : List.of();
        return new ProductVersionView(
                config.getProductCode(),
                config.getVersion(),
                config.getMinAge(),
                config.getLimitMin(),
                config.getLimitMax(),
                config.getActive(),
                channelsList,
                config.getEffectiveFrom(),
                current
        );
    }
}