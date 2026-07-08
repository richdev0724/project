package com.marketmono.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.dto.whop.WhopMembershipAttachRequest;
import com.marketmono.dto.whop.WhopMembershipAttachResponse;
import com.marketmono.dto.whop.WhopMembershipResolveWithoutActionRequest;
import com.marketmono.dto.whop.WhopMembershipReviewNoteRequest;
import com.marketmono.dto.whop.WhopMembershipReviewDetailDto;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.service.WhopMembershipAdminReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WhopMembershipAdminReviewControllerTest {

    private final WhopMembershipAdminReviewService reviewService = mock(WhopMembershipAdminReviewService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new WhopMembershipAdminReviewController(reviewService))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldListWhopMembershipReviews() throws Exception {
        when(reviewService.listReviews(
            eq(WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT),
            eq("buyer@example.com"),
            eq(false),
            any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/whop/membership-reviews")
                .param("status", "PUBLIC_NO_ATTEMPT")
                .param("search", "buyer@example.com")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldListNewestWhopMembershipReviewsFirst() throws Exception {
        when(reviewService.listReviews(
            isNull(),
            isNull(),
            eq(false),
            any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/whop/membership-reviews"))
            .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(reviewService).listReviews(isNull(), isNull(), eq(false), pageRequestCaptor.capture());
        PageRequest pageRequest = pageRequestCaptor.getValue();
        assertThat(pageRequest.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageRequest.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void shouldListResolvedWhopMembershipReviewsWhenRequested() throws Exception {
        when(reviewService.listReviews(
            isNull(),
            isNull(),
            eq(true),
            any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/whop/membership-reviews")
                .param("includeResolved", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldReturnWhopMembershipReviewDetail() throws Exception {
        UUID correlationId = UUID.randomUUID();
        when(reviewService.getReviewDetail(correlationId)).thenReturn(new WhopMembershipReviewDetailDto(
            correlationId,
            "mem_orphan",
            "buyer@example.com",
            WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT,
            "Needs review",
            UUID.randomUUID(),
            "Market Mono Monthly",
            "prod_public",
            "plan_monthly",
            null,
            null,
            null,
            List.of(),
            List.of()
        ));

        mockMvc.perform(get("/api/v1/admin/whop/membership-reviews/{correlationId}", correlationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
            .andExpect(jsonPath("$.status").value("PUBLIC_NO_ATTEMPT"));
    }

    @Test
    void shouldAttachWhopMembershipReviewUsingInternalAdminActor() throws Exception {
        UUID correlationId = UUID.randomUUID();
        UUID selectedUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new InternalAdminPrincipal(adminId),
            null,
            new InternalAdminPrincipal(adminId).getAuthorities()
        ));
        when(reviewService.attachMembership(
            eq(correlationId),
            any(WhopMembershipAttachRequest.class),
            eq(adminId),
            eq("203.0.113.10"),
            eq("JUnit")
        )).thenReturn(new WhopMembershipAttachResponse(
            correlationId,
            selectedUserId,
            subscriptionId,
            WhopMembershipCorrelationStatus.MATCHED,
            1
        ));

        mockMvc.perform(post("/api/v1/admin/whop/membership-reviews/{correlationId}/attach", correlationId)
                .contentType("application/json")
                .header("X-Forwarded-For", "203.0.113.10")
                .header("User-Agent", "JUnit")
                .content(objectMapper.writeValueAsString(new WhopMembershipAttachRequest(
                    selectedUserId,
                    "Whop payment verified",
                    false
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscriptionId").value(subscriptionId.toString()))
            .andExpect(jsonPath("$.expiredCheckoutAttempts").value(1));

        verify(reviewService).attachMembership(
            eq(correlationId),
            any(WhopMembershipAttachRequest.class),
            eq(adminId),
            eq("203.0.113.10"),
            eq("JUnit")
        );
    }

    @Test
    void shouldResolveWhopMembershipReviewWithoutActionUsingInternalAdminActor() throws Exception {
        UUID correlationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new InternalAdminPrincipal(adminId),
            null,
            new InternalAdminPrincipal(adminId).getAuthorities()
        ));
        when(reviewService.resolveWithoutAction(
            eq(correlationId),
            eq("Duplicate testing case resolved in Whop"),
            eq(adminId),
            eq("203.0.113.10"),
            eq("JUnit")
        )).thenReturn(new WhopMembershipReviewDetailDto(
            correlationId,
            "mem_orphan",
            "buyer@example.com",
            WhopMembershipCorrelationStatus.RESOLVED_NO_ACTION,
            "Duplicate testing case resolved in Whop",
            UUID.randomUUID(),
            "Market Mono Monthly",
            "prod_public",
            "plan_monthly",
            null,
            null,
            null,
            List.of(),
            List.of()
        ));

        mockMvc.perform(post(
                "/api/v1/admin/whop/membership-reviews/{correlationId}/resolve-without-action",
                correlationId
            )
                .contentType("application/json")
                .header("X-Forwarded-For", "203.0.113.10")
                .header("User-Agent", "JUnit")
                .content(objectMapper.writeValueAsString(new WhopMembershipResolveWithoutActionRequest(
                    "Duplicate testing case resolved in Whop"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED_NO_ACTION"))
            .andExpect(jsonPath("$.reviewReason").value("Duplicate testing case resolved in Whop"));

        verify(reviewService).resolveWithoutAction(
            correlationId,
            "Duplicate testing case resolved in Whop",
            adminId,
            "203.0.113.10",
            "JUnit"
        );
    }

    @Test
    void shouldUpdateReviewNoteUsingInternalAdminActor() throws Exception {
        UUID correlationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new InternalAdminPrincipal(adminId),
            null,
            new InternalAdminPrincipal(adminId).getAuthorities()
        ));
        when(reviewService.updateReviewNote(
            eq(correlationId),
            eq("Waiting for customer reply")
        )).thenReturn(new WhopMembershipReviewDetailDto(
            correlationId,
            "mem_orphan",
            "buyer@example.com",
            WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT,
            "Waiting for customer reply",
            UUID.randomUUID(),
            "Market Mono Monthly",
            "prod_public",
            "plan_monthly",
            null,
            null,
            null,
            List.of(),
            List.of()
        ));

        mockMvc.perform(post("/api/v1/admin/whop/membership-reviews/{correlationId}/review-note", correlationId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new WhopMembershipReviewNoteRequest(
                    "Waiting for customer reply"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewReason").value("Waiting for customer reply"));

        verify(reviewService).updateReviewNote(correlationId, "Waiting for customer reply");
    }
}
