package com.marketmono.service;

import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.repository.ProductRepository;
import com.stripe.model.Price;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {
    
    private final ProductRepository productRepository;
    
    @Transactional
    public Product syncProduct(com.stripe.model.Product stripeProduct) {
        Product product = productRepository.findByStripeProductId(stripeProduct.getId())
            .orElse(new Product());
        
        boolean isNew = product.getId() == null;
        
        // Sync core fields from Stripe
        product.setStripeProductId(stripeProduct.getId());
        product.setName(stripeProduct.getName());
        product.setDescription(stripeProduct.getDescription());
        product.setIsActive(Boolean.TRUE.equals(stripeProduct.getActive()));
        
        // Generate slug if new product
        if (isNew && (product.getSlug() == null || product.getSlug().isBlank())) {
            product.setSlug(generateSlug(stripeProduct.getName()));
        }
        
        product = productRepository.save(product);
        log.info("{} product: {} (Stripe ID: {})", 
            isNew ? "Created" : "Updated", product.getName(), product.getStripeProductId());
        return product;
    }

    @Transactional
    public void archiveProduct(String stripeProductId) {
        Product product = productRepository.findByStripeProductId(stripeProductId)
            .orElseThrow(() -> new IllegalStateException("Product not found: " + stripeProductId));

        product.setIsActive(false);
        product.setIsArchived(true);
        productRepository.save(product);

        log.info("Archived product {} ({})", product.getName(), stripeProductId);
    }
    
    @Transactional
    public void syncPrice(Price stripePrice) {
        String productStripeId = stripePrice.getProduct();
        Product product = productRepository.findByStripeProductId(productStripeId)
            .orElseThrow(() -> new IllegalStateException("Product not found: " + productStripeId));
        
        String interval = stripePrice.getRecurring() != null ? stripePrice.getRecurring().getInterval() : null;
        Long amount = stripePrice.getUnitAmount();
        
        if (amount == null) return;
        
        // Determine billing period from Stripe interval
        ProductPrice.BillingPeriod billingPeriod;
        int sortOrder;
        if ("month".equalsIgnoreCase(interval)) {
            billingPeriod = ProductPrice.BillingPeriod.MONTHLY;
            sortOrder = 1;
        } else if ("year".equalsIgnoreCase(interval)) {
            billingPeriod = ProductPrice.BillingPeriod.ANNUAL;
            sortOrder = 2;
        } else {
            log.warn("Unknown interval {} for price {}", interval, stripePrice.getId());
            return;
        }
        
        // Find existing price for this billing period or create new one
        ProductPrice productPrice = product.getPrices().stream()
            .filter(p -> p.getBillingPeriod() == billingPeriod)
            .findFirst()
            .orElseGet(() -> {
                ProductPrice newPrice = ProductPrice.builder()
                    .product(product)
                    .billingPeriod(billingPeriod)
                    .isActive(true)
                    .sortOrder(sortOrder)
                    .build();
                product.addPrice(newPrice);
                return newPrice;
            });
        
        // Update price fields from Stripe
        productPrice.setStripePriceId(stripePrice.getId());
        productPrice.setPriceCents(amount.intValue());
        productPrice.setIsActive(Boolean.TRUE.equals(stripePrice.getActive()));
        
        productRepository.save(product);
        log.info("Updated {} price for product {} ({})", billingPeriod, product.getName(), stripePrice.getId());
    }
    
    private String generateSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
    }
}
