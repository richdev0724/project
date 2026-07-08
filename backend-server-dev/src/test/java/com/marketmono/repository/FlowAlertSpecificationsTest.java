package com.marketmono.repository;

import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTypeEnum;
import com.marketmono.repository.FlowAlertSpecifications.FlowAlertFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowAlertSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildOtmPredicateForCallsAbovePriceAndPutsBelowPrice() {
        Root<FlowAlert> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<BigDecimal> strikePath = mock(Path.class);
        Path<BigDecimal> underlyingPath = mock(Path.class);
        Path<OptionTypeEnum> optionTypePath = mock(Path.class);
        Predicate putTypePredicate = mock(Predicate.class);
        Predicate callTypePredicate = mock(Predicate.class);
        Predicate putOtmPredicate = mock(Predicate.class);
        Predicate callOtmPredicate = mock(Predicate.class);

        when(root.<BigDecimal>get("strike")).thenReturn(strikePath);
        when(root.<BigDecimal>get("underlyingPrice")).thenReturn(underlyingPath);
        when(root.<OptionTypeEnum>get("optionType")).thenReturn(optionTypePath);
        when(cb.equal(optionTypePath, OptionTypeEnum.PUT)).thenReturn(putTypePredicate);
        when(cb.equal(optionTypePath, OptionTypeEnum.CALL)).thenReturn(callTypePredicate);
        when(cb.greaterThan(underlyingPath, strikePath)).thenReturn(putOtmPredicate);
        when(cb.greaterThan(strikePath, underlyingPath)).thenReturn(callOtmPredicate);

        Specification<FlowAlert> specification = FlowAlertSpecifications.buildSpecification(otmOnlyFilter());
        specification.toPredicate(root, query, cb);

        verify(cb).and(putTypePredicate, putOtmPredicate);
        verify(cb).and(callTypePredicate, callOtmPredicate);
    }

    private static FlowAlertFilter otmOnlyFilter() {
        return new FlowAlertFilter(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            null,
            null,
            null,
            null
        );
    }
}
