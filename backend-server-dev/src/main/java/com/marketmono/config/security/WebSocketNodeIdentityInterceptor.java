package com.marketmono.config.security;

import com.marketmono.service.NodeIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketNodeIdentityInterceptor implements ChannelInterceptor {

    private static final String NODE_ID_HEADER = "X-Node-Id";

    private final NodeIdentityService nodeIdentityService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECTED.equals(accessor.getCommand())) {
            accessor.setNativeHeader(NODE_ID_HEADER, nodeIdentityService.getNodeId());
        }

        return message;
    }
}