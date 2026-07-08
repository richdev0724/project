package com.marketmono.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockInformationResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeTagsWithNeutralFieldName() throws Exception {
        StockInformationResponse response = new StockInformationResponse(new StockInformationResponse.StockInformationData(
            null,
            null,
            "NVIDIA Corporation",
            null,
            null,
            null,
            true,
            "Common Stock",
            "3000000000000",
            "big",
            null,
            "Technology",
            "Accelerated computing company",
            "NVDA",
            List.of("semiconductors")
        ));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"source_tags\":[\"semiconductors\"]");
        assertThat(json).doesNotContain("uw_tags");
    }

    @Test
    void shouldReadLegacyUpstreamTagsIntoNeutralField() throws Exception {
        String json = """
            {
              "data": {
                "full_name": "NVIDIA Corporation",
                "symbol": "NVDA",
                "uw_tags": ["semiconductors"]
              }
            }
            """;

        StockInformationResponse response = objectMapper.readValue(json, StockInformationResponse.class);

        assertThat(response.data().sourceTags()).containsExactly("semiconductors");
    }
}
