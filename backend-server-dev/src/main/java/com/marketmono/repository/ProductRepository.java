package com.marketmono.repository;

import com.marketmono.entity.Product;
import com.marketmono.entity.ProductVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Product entity operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    
    /**
     * Find product by slug.
     */
    Optional<Product> findBySlug(String slug);
    
    /**
     * Find product by name (case-insensitive).
     */
    Optional<Product> findByNameIgnoreCase(String name);
    
    /**
     * Find all active (non-archived) products ordered by sort order.
     */
    List<Product> findByIsArchivedFalseOrderBySortOrderAsc();
    
    /**
     * Find all products (including archived) ordered by sort order.
     */
    List<Product> findAllByOrderBySortOrderAsc();
    
    /**
     * Find all active products visible to users (for storefront).
     */
    List<Product> findByIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc();

    /**
     * Find all active public products visible on generic storefront and checkout surfaces.
     */
    List<Product> findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility visibility);

    /**
     * Find the first active product for a direct-link-only flow such as Whop Beta.
     */
    Optional<Product> findFirstByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility visibility);
    
    /**
     * Check if a product with the given slug exists.
     */
    boolean existsBySlug(String slug);
    
    /**
     * Check if a product with the given name exists (case-insensitive).
     */
    boolean existsByNameIgnoreCase(String name);
    
    /**
     * Check if slug exists for another product (for updates).
     */
    @Query("""
        SELECT COUNT(p) 
        FROM Product p 
        WHERE p.slug = :slug AND p.id != :productId
        """)
    long countBySlugAndIdNot(@Param("slug") String slug, @Param("productId") UUID productId);

    default boolean existsBySlugAndIdNot(String slug, UUID productId) {
        return countBySlugAndIdNot(slug, productId) > 0;
    }
    
    /**
     * Find products by Stripe price ID (via product_prices table).
     */
    @Query("""
        SELECT p FROM Product p 
        JOIN p.prices pp 
        WHERE pp.stripePriceId = :priceId
        """)
    Optional<Product> findByStripePriceId(@Param("priceId") String priceId);
    
    /**
     * Find product by Stripe product ID.
     */
    Optional<Product> findByStripeProductId(String stripeProductId);

    /**
     * Find product by Whop product ID.
     */
    Optional<Product> findByWhopProductId(String whopProductId);

    /**
     * Find product by Whop plan ID (via product_prices table).
     */
    @Query("""
        SELECT p FROM Product p
        JOIN p.prices pp
        WHERE pp.whopPlanId = :planId
        """)
    Optional<Product> findByWhopPlanId(@Param("planId") String planId);
    
    /**
     * Count active products (for validation).
     */
    long countByIsArchivedFalse();
    
    /**
     * Find archived products.
     */
    List<Product> findByIsArchivedTrueOrderByUpdatedAtDesc();
}
