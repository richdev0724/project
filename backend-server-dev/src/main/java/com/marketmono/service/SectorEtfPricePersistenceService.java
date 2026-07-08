package com.marketmono.service;

import com.marketmono.entity.SectorEtfPrice;
import com.marketmono.repository.SectorEtfPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SectorEtfPricePersistenceService {

    private final SectorEtfPriceRepository sectorEtfPriceRepository;

    @Transactional(readOnly = true)
    public boolean existsForDate(String ticker, LocalDate date) {
        return sectorEtfPriceRepository.existsByTickerAndDate(ticker, date);
    }

    @Transactional
    public SectorEtfPrice save(SectorEtfPrice price) {
        return sectorEtfPriceRepository.save(price);
    }
}
