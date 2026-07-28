package com.neobank.module.controller;

import com.neobank.module.dto.CreateProductVersionRequest;
import com.neobank.module.dto.ProductVersionCreated;
import com.neobank.module.dto.ProductVersionView;
import com.neobank.module.service.ProductConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductConfigController {

    private final ProductConfigService productConfigService;

    public ProductConfigController(ProductConfigService productConfigService) {
        this.productConfigService = productConfigService;
    }

    @PostMapping
    public ResponseEntity<?> createVersion(@Valid @RequestBody CreateProductVersionRequest request) {
        try {
            ProductVersionCreated created = productConfigService.createVersion(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{code}/versions")
    public ResponseEntity<?> getVersions(@PathVariable String code) {
        try {
            List<ProductVersionView> versions = productConfigService.getVersions(code);
            return ResponseEntity.ok(versions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<String>> getAllProductCodes() {
        return ResponseEntity.ok(productConfigService.getAllProductCodes());
    }
}