package com.marketmono.controller;

import com.marketmono.dto.*;
import com.marketmono.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Product operations.
 * 
 * Public endpoints:
 * - GET /api/v1/products/storefront - Available products for subscription
 * - GET /api/v1/products/storefront/{slug} - Single product details
 * 
 * Admin endpoints:
 * - GET /api/v1/admin/products - All products (including archived)
 * - GET /api/v1/admin/products/{id} - Product by ID
 * - POST /api/v1/admin/products - Create product
 * - PUT /api/v1/admin/products/{id} - Update product
 * - POST /api/v1/admin/products/{id}/archive - Archive product
 * - POST /api/v1/admin/products/{id}/restore - Restore archived product
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    
    private final ProductService productService;
    
    // ============================================
    // Public Storefront Endpoints
    // ============================================
    
    /**
     * Get products available for subscription (public storefront).
     * GET /api/v1/products/storefront
     */
    @GetMapping("/api/v1/products/storefront")
    public ResponseEntity<List<ProductStorefrontDto>> getStorefrontProducts() {
        log.debug("GET /api/v1/products/storefront");
        List<ProductStorefrontDto> products = productService.getStorefrontProducts();
        return ResponseEntity.ok(products);
    }
    
    /**
     * Get single product by slug for storefront.
     * GET /api/v1/products/storefront/{slug}
     */
    @GetMapping("/api/v1/products/storefront/{slug}")
    public ResponseEntity<ProductStorefrontDto> getStorefrontProductBySlug(@PathVariable String slug) {
        log.debug("GET /api/v1/products/storefront/{}", slug);
        ProductStorefrontDto product = productService.getStorefrontProductBySlug(slug);
        return ResponseEntity.ok(product);
    }
    
    /**
     * Get product summaries for list views.
     * GET /api/v1/products/summaries
     */
    @GetMapping("/api/v1/products/summaries")
    public ResponseEntity<List<ProductSummaryDto>> getProductSummaries() {
        log.debug("GET /api/v1/products/summaries");
        List<ProductSummaryDto> summaries = productService.getProductSummaries();
        return ResponseEntity.ok(summaries);
    }
    
    // ============================================
    // Admin Endpoints
    // ============================================
    
    /**
     * Get all products (admin view, includes archived).
     * GET /api/v1/admin/products
     */
    @GetMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        log.debug("GET /api/v1/admin/products");
        List<ProductDto> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }
    
    /**
     * Get single product by ID (admin view).
     * GET /api/v1/admin/products/{id}
     */
    @GetMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> getProductById(@PathVariable UUID id) {
        log.debug("GET /api/v1/admin/products/{}", id);
        ProductDto product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }
    
    /**
     * Get product by slug (admin view).
     * GET /api/v1/admin/products/slug/{slug}
     */
    @GetMapping("/api/v1/admin/products/slug/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> getProductBySlug(@PathVariable String slug) {
        log.debug("GET /api/v1/admin/products/slug/{}", slug);
        ProductDto product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(product);
    }
    
    /**
     * Create a new product.
     * POST /api/v1/admin/products
     */
    @PostMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("POST /api/v1/admin/products - Creating product: {}", request.name());
        ProductDto created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Update an existing product.
     * PUT /api/v1/admin/products/{id}
     */
    @PutMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("PUT /api/v1/admin/products/{} - Updating product", id);
        
        // Ensure the ID from path matches the request
        if (!id.equals(request.id())) {
            return ResponseEntity.badRequest().build();
        }
        
        ProductDto updated = productService.updateProduct(request);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Archive a product (soft delete).
     * POST /api/v1/admin/products/{id}/archive
     */
    @PostMapping("/api/v1/admin/products/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> archiveProduct(@PathVariable UUID id) {
        log.info("POST /api/v1/admin/products/{}/archive - Archiving product", id);
        ProductDto archived = productService.archiveProduct(id);
        return ResponseEntity.ok(archived);
    }
    
    /**
     * Restore an archived product.
     * POST /api/v1/admin/products/{id}/restore
     */
    @PostMapping("/api/v1/admin/products/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> restoreProduct(@PathVariable UUID id) {
        log.info("POST /api/v1/admin/products/{}/restore - Restoring product", id);
        ProductDto restored = productService.restoreProduct(id);
        return ResponseEntity.ok(restored);
    }
    
    // ============================================
    // Exception Handlers
    // ============================================
    
    @ExceptionHandler(ProductService.ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductService.ProductNotFoundException ex) {
        log.warn("Product not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(ProductService.ProductNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleProductNotAvailable(ProductService.ProductNotAvailableException ex) {
        log.warn("Product not available: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new ErrorResponse("PRODUCT_NOT_AVAILABLE", ex.getMessage()));
    }
    
    @ExceptionHandler(ProductService.DuplicateProductException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateProduct(ProductService.DuplicateProductException ex) {
        log.warn("Duplicate product: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("DUPLICATE_PRODUCT", ex.getMessage()));
    }
    
    /**
     * Standard error response format.
     */
    public record ErrorResponse(String code, String message) {}
}
