package com.marketmono.dto.whop;

import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhopMembershipAttachRequestValidationTest {

    @Test
    void reviewNoteReasonIsOptionalWhenAttachingMembership() throws NoSuchFieldException {
        var reasonField = WhopMembershipAttachRequest.class.getDeclaredField("reason");

        assertThat(WhopMembershipAttachRequest.class.getRecordComponents())
            .anySatisfy(component -> {
                if ("reason".equals(component.getName())) {
                    assertThat(component.getAnnotation(NotBlank.class)).isNull();
                }
            });
        assertThat(reasonField.getAnnotation(NotBlank.class)).isNull();
    }
}
