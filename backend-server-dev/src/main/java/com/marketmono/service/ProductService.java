package com.marketmono.service;

import com.marketmono.dto.*;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for Product CRUD operations and business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final ProductRepository productRepository;
    
    // ============================================
    // Read Operations
    // ============================================
    
    /**
     * Get all products (including archived) for admin.
     */
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        return productRepository.findAllByOrderBySortOrderAsc().stream()
            .map(ProductDto::from)
            .toList();
    }
    
    /**
     * Get all active (non-archived) products.
     */
    @Transactional(readOnly = true)
    public List<ProductDto> getActiveProducts() {
        return productRepository.findByIsArchivedFalseOrderBySortOrderAsc().stream()
            .map(ProductDto::from)
            .toList();
    }
    
    /**
     * Get products available for subscription (active and not archived).
     */
    @Transactional(readOnly = true)
    public List<ProductStorefrontDto> getStorefrontProducts() {
        return productRepository
            .findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC)
            .stream()
            .map(ProductStorefrontDto::from)
            .toList();
    }
    
    /**
     * Get product summaries for list views.
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getProductSummaries() {
        return productRepository
            .findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC)
            .stream()
            .map(ProductSummaryDto::from)
            .toList();
    }
    
    /**
     * Get a single product by ID.
     */
    @Transactional(readOnly = true)
    public ProductDto getProductById(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        return ProductDto.from(product);
    }
    
    /**
     * Get a single product by slug.
     */
    @Transactional(readOnly = true)
    public ProductDto getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with slug: " + slug));
        return ProductDto.from(product);
    }
    
    /**
     * Get storefront product by slug.
     */
    @Transactional(readOnly = true)
    public ProductStorefrontDto getStorefrontProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with slug: " + slug));
        
        if (!product.isPubliclyAvailableForSubscription()) {
            throw new ProductNotAvailableException("Product is not available for subscription: " + slug);
        }
        
        return ProductStorefrontDto.from(product);
    }
    
    // ============================================
    // Create Operations
    // ============================================
    
    /**
     * Create a new product.
     */
    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        // Validate uniqueness
        if (productRepository.existsBySlug(request.slug())) {
            throw new DuplicateProductException("Product with slug already exists: " + request.slug());
        }
        if (productRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateProductException("Product with name already exists: " + request.name());
        }
        
        Product product = Product.builder()
            .name(request.name())
            .slug(request.slug())
            .description(request.description())
            .shortDescription(request.shortDescription())
            .stripeProductId(request.stripeProductId())
            .sortOrder(request.sortOrder())
            .isActive(request.isActive())
            .visibility(parseVisibility(request.visibility()))
            .trialDays(request.trialDays())
            .build();
        
        // Add prices
        for (var priceReq : request.prices()) {
            var price = ProductPrice.builder()
                .product(product)
                .billingPeriod(ProductPrice.BillingPeriod.valueOf(priceReq.billingPeriod()))
                .priceCents(priceReq.priceCents())
                .stripePriceId(priceReq.stripePriceId())
                .isActive(priceReq.isActive())
                .sortOrder(priceReq.sortOrder())
                .build();
            product.addPrice(price);
        }
        
        Product saved = productRepository.save(product);
        log.info("Created product: {} (slug: {}, id: {}) with {} prices", 
            saved.getName(), saved.getSlug(), saved.getId(), saved.getPrices().size());
        
        return ProductDto.from(saved);
    }
    
    // ============================================
    // Update Operations
    // ============================================
    
    /**
     * Update an existing product.
     */
    @Transactional
    public ProductDto updateProduct(UpdateProductRequest request) {
        Product product = productRepository.findById(request.id())
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + request.id()));
        
        // Validate slug uniqueness if changing
        if (request.slug() != null && !request.slug().equals(product.getSlug())) {
            if (productRepository.existsBySlugAndIdNot(request.slug(), request.id())) {
                throw new DuplicateProductException("Product with slug already exists: " + request.slug());
            }
            product.setSlug(request.slug());
        }
        
        // Update fields if provided
        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.shortDescription() != null) product.setShortDescription(request.shortDescription());
        if (request.stripeProductId() != null) product.setStripeProductId(request.stripeProductId());
        if (request.sortOrder() != null) product.setSortOrder(request.sortOrder());
        if (request.isActive() != null) product.setIsActive(request.isActive());
        if (request.visibility() != null) product.setVisibility(parseVisibility(request.visibility()));
        if (request.trialDays() != null) product.setTrialDays(request.trialDays());
        
        // Update prices if provided
        if (request.prices() != null) {
            // Clear existing prices
            product.getPrices().clear();
            
            // Add new prices
            for (var priceReq : request.prices()) {
                var price = ProductPrice.builder()
                    .product(product)
                    .billingPeriod(ProductPrice.BillingPeriod.valueOf(priceReq.billingPeriod()))
                    .priceCents(priceReq.priceCents())
                    .stripePriceId(priceReq.stripePriceId())
                    .isActive(priceReq.isActive() == null || priceReq.isActive())
                    .sortOrder(priceReq.sortOrder() != null ? priceReq.sortOrder() : 0)
                    .build();
                
                if (priceReq.id() != null) {
                    price.setId(priceReq.id());
                }
                
                product.addPrice(price);
            }
        }
        
        Product updated = productRepository.save(product);
        log.info("Updated product: {} (id: {})", updated.getName(), updated.getId());
        
        return ProductDto.from(updated);
    }
    
    // ============================================
    // Archive Operations
    // ============================================
    
    /**
     * Archive a product (soft delete).
     * Archived products are not available for new subscriptions but existing subscriptions continue.
     */
    @Transactional
    public ProductDto archiveProduct(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        
        if (Boolean.TRUE.equals(product.getIsArchived())) {
            throw new IllegalStateException("Product is already archived: " + id);
        }
        
        product.setIsArchived(true);
        product.setIsActive(false);
        
        Product archived = productRepository.save(product);
        log.info("Archived product: {} (id: {})", archived.getName(), archived.getId());
        
        return ProductDto.from(archived);
    }
    
    /**
     * Restore an archived product.
     */
    @Transactional
    public ProductDto restoreProduct(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        
        if (!Boolean.TRUE.equals(product.getIsArchived())) {
            throw new IllegalStateException("Product is not archived: " + id);
        }
        
        product.setIsArchived(false);
        // Note: isActive remains false - admin must explicitly activate
        
        Product restored = productRepository.save(product);
        log.info("Restored product: {} (id: {})", restored.getName(), restored.getId());
        
        return ProductDto.from(restored);
    }
    
    // ============================================
    // Exception Classes
    // ============================================
    
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class ProductNotAvailableException extends RuntimeException {
        public ProductNotAvailableException(String message) {
            super(message);
        }
    }
    
    public static class DuplicateProductException extends RuntimeException {
        public DuplicateProductException(String message) {
            super(message);
        }
    }

    private ProductVisibility parseVisibility(String value) {
        if (value == null || value.isBlank()) {
            return ProductVisibility.PUBLIC;
        }
        try {
            return ProductVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported product visibility: " + value);
        }
    }
}
