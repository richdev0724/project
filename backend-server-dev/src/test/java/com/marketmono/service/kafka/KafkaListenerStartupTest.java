package com.marketmono.service.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.marketmono.service.spotgex.SpotGexLiveGexKafkaStreamAdapter.LISTENER_ID;

class KafkaListenerStartupTest {

    @Test
    void shouldStartRegisteredListenersExceptManualSpotGexListener() {
        KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
        MessageListenerContainer normalContainer = mock(MessageListenerContainer.class);
        MessageListenerContainer spotGexContainer = mock(MessageListenerContainer.class);
        when(normalContainer.isRunning()).thenReturn(false);
        when(spotGexContainer.isRunning()).thenReturn(false);
        when(registry.getListenerContainers()).thenReturn(List.of(normalContainer, spotGexContainer));
        when(registry.getListenerContainer(LISTENER_ID)).thenReturn(spotGexContainer);

        new KafkaListenerStartup(registry).startListeners();

        verify(normalContainer).start();
        verify(spotGexContainer, never()).start();
        verify(registry, never()).start();
    }

    @Test
    void shouldNotRetryWhenOnlyManualSpotGexListenerIsStopped() {
        KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
        MessageListenerContainer normalContainer = mock(MessageListenerContainer.class);
        MessageListenerContainer spotGexContainer = mock(MessageListenerContainer.class);
        when(normalContainer.isRunning()).thenReturn(true);
        when(spotGexContainer.isRunning()).thenReturn(false);
        when(registry.getListenerContainers()).thenReturn(List.of(normalContainer, spotGexContainer));
        when(registry.getListenerContainer(LISTENER_ID)).thenReturn(spotGexContainer);

        new KafkaListenerStartup(registry).retryStartListeners();

        verify(normalContainer, never()).start();
        verify(spotGexContainer, never()).start();
        verify(registry, never()).start();
    }
}
