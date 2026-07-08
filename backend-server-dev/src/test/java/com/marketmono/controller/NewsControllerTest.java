package com.marketmono.controller;

import com.marketmono.dto.CompanyProfileDto;
import com.marketmono.dto.NewsResponseDto;
import com.marketmono.service.NewsService;
import com.marketmono.service.TopHeadlinesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private NewsService newsService;

    @Mock
    private TopHeadlinesService topHeadlinesService;

    @Test
    void headlinesShouldPassProviderSelectorAndTopicToService() throws Exception {
        when(newsService.getNewsHeadlines(
            "ignored-sources",
            "ignored-search",
            true,
            5,
            3,
            "aapl",
            "newsapiai",
            "global"
        )).thenReturn(NewsResponseDto.success(List.of()));

        mockMvc()
            .perform(get("/api/v1/news/headlines")
                .param("sources", "ignored-sources")
                .param("search_term", "ignored-search")
                .param("major_only", "true")
                .param("limit", "5")
                .param("page", "3")
                .param("ticker", "aapl")
                .param("source", "newsapiai")
                .param("topic", "global"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(newsService).getNewsHeadlines(
            "ignored-sources",
            "ignored-search",
            true,
            5,
            3,
            "aapl",
            "newsapiai",
            "global"
        );
    }

    @Test
    void headlinesShouldForwardMarketDataSourceSelectorToService() throws Exception {
        when(newsService.getNewsHeadlines(
            null,
            null,
            null,
            25,
            null,
            null,
            "market_data",
            null
        )).thenReturn(NewsResponseDto.success(List.of()));

        mockMvc()
            .perform(get("/api/v1/news/headlines")
                .param("limit", "25")
                .param("source", "market_data"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(newsService).getNewsHeadlines(
            null,
            null,
            null,
            25,
            null,
            null,
            "market_data",
            null
        );
    }

    @Test
    void headlinesShouldForwardSelectedDateToService() throws Exception {
        when(newsService.getNewsHeadlines(
            null,
            null,
            null,
            100,
            null,
            null,
            "market_data",
            null,
            java.time.LocalDate.of(2026, 6, 23)
        )).thenReturn(NewsResponseDto.success(List.of()));

        mockMvc()
            .perform(get("/api/v1/news/headlines")
                .param("limit", "100")
                .param("source", "market_data")
                .param("date", "2026-06-23"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(newsService).getNewsHeadlines(
            null,
            null,
            null,
            100,
            null,
            null,
            "market_data",
            null,
            java.time.LocalDate.of(2026, 6, 23)
        );
    }

    @Test
    void companyProfileShouldReturnServiceProfile() throws Exception {
        when(newsService.getCompanyProfile("NVDA"))
            .thenReturn(new CompanyProfileDto(
                "NVIDIA Corporation",
                "Accelerated computing company",
                "Semiconductors",
                "Technology",
                "Jensen Huang",
                "https://www.nvidia.com",
                29600,
                3_000_000_000_000L,
                "US",
                "Santa Clara",
                "CA",
                "2788 San Tomas Expressway",
                "408-486-2000",
                "NASDAQ",
                "USD",
                false,
                true
            ));

        mockMvc()
            .perform(get("/api/v1/news/company-profile/NVDA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("NVIDIA Corporation"))
            .andExpect(jsonPath("$.sector").value("Technology"));
    }

    @Test
    void companyProfileShouldReturnNotFoundWhenServiceHasNoProfile() throws Exception {
        when(newsService.getCompanyProfile("MISSING")).thenReturn(null);

        mockMvc()
            .perform(get("/api/v1/news/company-profile/MISSING"))
            .andExpect(status().isNotFound());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(new NewsController(newsService, topHeadlinesService))
            .build();
    }
}
