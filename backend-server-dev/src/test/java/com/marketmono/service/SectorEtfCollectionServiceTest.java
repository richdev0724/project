package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.UnusualWhalesTickerOhlcDto;
import com.marketmono.entity.SectorEtfPrice;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SectorEtfCollectionServiceTest {

    @Test
    void collectionLoopMethodsDoNotOpenTransactions() throws NoSuchMethodException {
        assertThat(method("collectHourlyPrices").getAnnotation(Transactional.class)).isNull();
        assertThat(method("backfillDate", LocalDate.class).getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void serviceRoutesDatabaseAccessThroughPersistenceCollaborator() {
        List<String> fieldTypes = Arrays.stream(SectorEtfCollectionService.class.getDeclaredFields())
            .map(field -> field.getType().getSimpleName())
            .toList();

        assertThat(fieldTypes)
            .contains("SectorEtfPricePersistenceService")
            .doesNotContain("SectorEtfPriceRepository");
    }

    @Test
    void hourlyCollectionCallsUnusualWhalesOutsideTransactionAndDelegatesPersistence() {
        LocalDate today = com.marketmono.util.DateUtils.getCurrentDateInEasternTime();
        UnusualWhalesClient unusualWhalesClient = mock(UnusualWhalesClient.class);
        SectorEtfPricePersistenceService persistenceService = mock(SectorEtfPricePersistenceService.class);
        AtomicBoolean transactionWasActiveDuringApiCall = new AtomicBoolean(true);
        SectorEtfCollectionService service = new SectorEtfCollectionService(unusualWhalesClient, persistenceService);

        when(persistenceService.existsForDate(anyString(), eq(today))).thenReturn(true);
        when(persistenceService.existsForDate("XLU", today)).thenReturn(false);
        when(unusualWhalesClient.getTickerOhlc("XLU", today.toString(), "1d"))
            .thenAnswer(invocation -> {
                transactionWasActiveDuringApiCall.set(TransactionSynchronizationManager.isActualTransactionActive());
                return ohlc("74.25");
            });

        service.collectHourlyPrices();

        assertThat(transactionWasActiveDuringApiCall).isFalse();
        verify(persistenceService).save(org.mockito.ArgumentMatchers.argThat(price ->
            "XLU".equals(price.getTicker())
                && today.equals(price.getDate())
                && new BigDecimal("74.25").compareTo(price.getClosePrice()) == 0
        ));
    }

    @Test
    void persistenceCollaboratorOwnsShortTransactions() throws ReflectiveOperationException {
        Class<?> persistenceService = Class.forName("com.marketmono.service.SectorEtfPricePersistenceService");

        Transactional existsTransaction = persistenceService
            .getDeclaredMethod("existsForDate", String.class, LocalDate.class)
            .getAnnotation(Transactional.class);
        assertThat(existsTransaction).isNotNull();
        assertThat(existsTransaction.readOnly()).isTrue();

        Transactional saveTransaction = persistenceService
            .getDeclaredMethod("save", SectorEtfPrice.class)
            .getAnnotation(Transactional.class);
        assertThat(saveTransaction).isNotNull();
        assertThat(saveTransaction.readOnly()).isFalse();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SectorEtfCollectionService.class.getDeclaredMethod(name, parameterTypes);
    }

    private static UnusualWhalesTickerOhlcDto ohlc(String close) {
        return new UnusualWhalesTickerOhlcDto(List.of(new UnusualWhalesTickerOhlcDto.TickerOhlcItem(
            "2026-05-30T00:00:00Z",
            "2026-05-30T23:59:59Z",
            "73.00",
            "75.00",
            "72.50",
            close,
            1_000L,
            1_000L,
            "regular"
        )));
    }
}
