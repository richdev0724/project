package com.marketmono.service;

import com.marketmono.entity.Product;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldUsePublicProductsForStorefrontProducts() {
        Product publicProduct = product("Market Mono", "market-mono", ProductVisibility.PUBLIC);
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(
            ProductVisibility.PUBLIC
        )).thenReturn(List.of(publicProduct));

        assertThat(productService.getStorefrontProducts())
            .singleElement()
            .satisfies(dto -> {
                assertThat(dto.slug()).isEqualTo("market-mono");
                assertThat(dto.visibility()).isEqualTo("PUBLIC");
                assertThat(dto.isAvailable()).isTrue();
            });
    }

    @Test
    void shouldUsePublicProductsForProductSummaries() {
        Product publicProduct = product("Market Mono", "market-mono", ProductVisibility.PUBLIC);
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(
            ProductVisibility.PUBLIC
        )).thenReturn(List.of(publicProduct));

        assertThat(productService.getProductSummaries())
            .singleElement()
            .satisfies(dto -> {
                assertThat(dto.slug()).isEqualTo("market-mono");
                assertThat(dto.visibility()).isEqualTo("PUBLIC");
            });
    }

    @Test
    void shouldRejectDirectLinkOnlyProductByStorefrontSlug() {
        when(productRepository.findBySlug("market-mono-beta"))
            .thenReturn(Optional.of(product(
                "Market Mono Beta",
                "market-mono-beta",
                ProductVisibility.DIRECT_LINK_ONLY
            )));

        assertThatThrownBy(() -> productService.getStorefrontProductBySlug("market-mono-beta"))
            .isInstanceOf(ProductService.ProductNotAvailableException.class)
            .hasMessageContaining("not available for subscription");
    }

    private static Product product(String name, String slug, ProductVisibility visibility) {
        return Product.builder()
            .id(UUID.randomUUID())
            .name(name)
            .slug(slug)
            .isActive(true)
            .isArchived(false)
            .visibility(visibility)
            .build();
    }
}
