package com.marketmono.config.security;

import com.marketmono.service.NodeIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketNodeIdentityInterceptorTest {

    @Mock
    private NodeIdentityService nodeIdentityService;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketNodeIdentityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketNodeIdentityInterceptor(nodeIdentityService);
    }

    @Test
    void shouldAddNodeIdHeaderToConnectedFrame() {
        when(nodeIdentityService.getNodeId()).thenReturn("backend-a-prod");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getFirstNativeHeader("X-Node-Id")).isEqualTo("backend-a-prod");
    }

    @Test
    void shouldLeaveNonConnectedFramesUnchanged() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getFirstNativeHeader("X-Node-Id")).isNull();
        verifyNoInteractions(nodeIdentityService);
    }
}