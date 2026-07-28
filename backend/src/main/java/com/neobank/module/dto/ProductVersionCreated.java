package com.neobank.module.dto;

/**
 * What {@code POST /products} returns — the newly created version number.
 *
 * <p>The version is automatically incremented from the highest existing version for the
 * given productCode. If no versions exist yet, it starts at 1.</p>
 */
public record ProductVersionCreated(Integer version) {
}