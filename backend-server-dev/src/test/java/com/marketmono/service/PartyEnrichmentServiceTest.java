package com.marketmono.service;

import com.marketmono.client.CongressGovClient;
import com.marketmono.dto.CongressGovMemberResponse;
import com.marketmono.dto.CongressMember;
import com.marketmono.entity.Politician;
import com.marketmono.repository.PoliticianRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartyEnrichmentServiceTest {

    @Test
    void enrichPoliticianIfNeededMatchesCongressNameAfterRemovingMiddleInitialsAndNicknames() {
        CongressGovClient congressGovClient = mock(CongressGovClient.class);
        PoliticianRepository politicianRepository = mock(PoliticianRepository.class);
        PartyEnrichmentService service = new PartyEnrichmentService(congressGovClient, politicianRepository);
        Politician politician = Politician.builder()
            .id(UUID.randomUUID())
            .name("Crawford, Eric A. \"Rick\"")
            .party("UNK")
            .build();

        when(congressGovClient.getCurrentMembers(600)).thenReturn(new CongressGovMemberResponse(List.of(
            new CongressMember(
                "C001",
                "Crawford, Eric",
                "Republican",
                "AR",
                "House of Representatives",
                "1"
            )
        )));

        assertThat(service.enrichPoliticianIfNeeded(politician)).isTrue();
        assertThat(politician.getParty()).isEqualTo("R");
        verify(politicianRepository).save(politician);
    }
}
