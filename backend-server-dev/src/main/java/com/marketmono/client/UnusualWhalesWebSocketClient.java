package com.marketmono.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.UwPriceTickDto;
import com.marketmono.service.spotgex.SpotGexStreamEvent;
import com.marketmono.service.spotgex.SpotGexWebSocketPayloadParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WebSocket client for streaming data from the upstream market data API.
 * REST quota reporting is handled in {@link UnusualWhalesClient}; this stream client does not expose the
 * daily REST quota headers used by the backend-admin reporting contract.
 *
 * <p>Subscribes to multiple channels on a single connection:
 * <ul>
 *   <li>{@code flow-alerts} — option flow alerts</li>
 *   <li>{@code off_lit_trades} — dark pool (off-lit) equity trades</li>
 *   <li>{@code news} — market data news headlines</li>
 * </ul>
 */
@Component
public class UnusualWhalesWebSocketClient {
    
    private static final Logger log = LoggerFactory.getLogger(UnusualWhalesWebSocketClient.class);
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP =
        new TypeReference<>() {};
    
    private final UnusualWhalesConfig config;
    private final ObjectMapper objectMapper;
    private final SpotGexWebSocketPayloadParser spotGexPayloadParser;
    private final ReactorNettyWebSocketClient webSocketClient;
    private final Sinks.Many<UnusualWhalesFlowAlertDto> flowAlertSink;
    private final Sinks.Many<DarkPoolRecentTradeDto> offLitTradeSink;
    private final Sinks.Many<NewsHeadlineDto> newsHeadlineSink;
    private final Sinks.Many<SpotGexStreamEvent> spotGexStreamSink;
    private final Set<String> desiredSpotGexChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> desiredPriceChannels = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    
    private Consumer<UnusualWhalesFlowAlertDto> flowAlertHandler;
    private Consumer<DarkPoolRecentTradeDto> offLitTradeHandler;
    private Consumer<NewsHeadlineDto> newsHeadlineHandler;
    private Consumer<SpotGexStreamEvent> spotGexStreamHandler;
    private Consumer<UwPriceTickDto> priceTickHandler;
    private volatile WebSocketSession activeSession;
    
    @Autowired
    public UnusualWhalesWebSocketClient(
        UnusualWhalesConfig config,
        ObjectMapper objectMapper,
        SpotGexWebSocketPayloadParser spotGexPayloadParser
    ) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.spotGexPayloadParser = spotGexPayloadParser;
        this.webSocketClient = new ReactorNettyWebSocketClient();
        this.flowAlertSink = Sinks.many().multicast().onBackpressureBuffer(
            config.getWebsocket().getMessageBufferSize()
        );
        this.offLitTradeSink = Sinks.many().multicast().onBackpressureBuffer(
            config.getWebsocket().getMessageBufferSize()
        );
        this.newsHeadlineSink = Sinks.many().multicast().onBackpressureBuffer(
            config.getWebsocket().getMessageBufferSize()
        );
        this.spotGexStreamSink = Sinks.many().multicast().onBackpressureBuffer(
            config.getWebsocket().getMessageBufferSize()
        );
    }
    
    /**
     * Connect to WebSocket and start receiving flow alerts and off-lit trades.
     */
    public Mono<Void> connect() {
        if (!config.getWebsocket().isEnabled()) {
            log.info("WS is disabled in configuration");
            return Mono.empty();
        }
        
        String baseWsUrl = config.getWebsocket().getUrl();
        String apiKey = config.getApi().getApiKey();
        
        // Build WebSocket URL with token as query parameter
        String wsUrl = baseWsUrl + "?token=" + apiKey;
        
        log.info("Connecting to market data WS at {}?token=***", baseWsUrl);
        
        WebSocketHandler handler = session -> {
            isConnected.set(true);
            activeSession = session;
            log.info("WS connected successfully (authenticated via URL token)");
            
            // Subscribe to channels. The upstream WS API uses JSON with "channel" + "msg_type" fields.
            subscribeToChannel(session, "flow-alerts");
            subscribeToChannel(session, "off_lit_trades");
            if (shouldSubscribeToNews()) {
                subscribeToChannel(session, "news");
            }
            desiredSpotGexChannels.forEach(channel -> subscribeToChannel(session, channel));
            desiredPriceChannels.forEach(channel -> subscribeToChannel(session, channel));
            
            // Handle incoming messages from all channels
            Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(this::routeMessage)
                .doOnError(e -> log.error("Error processing WebSocket message", e))
                .onErrorResume(_ -> Mono.empty())
                .then();
            
            return receive
                .doFinally(signalType -> {
                    isConnected.set(false);
                    activeSession = null;
                    log.info("WebSocket disconnected: {}", signalType);
                });
        };
        
        return webSocketClient.execute(URI.create(wsUrl), handler)
            .doOnError(e -> {
                isConnected.set(false);
                log.error("WebSocket connection error", e);
            })
            .retryWhen(reactor.util.retry.Retry.fixedDelay(
                config.getWebsocket().getMaxReconnectAttempts() < 0 ? 
                    Long.MAX_VALUE : config.getWebsocket().getMaxReconnectAttempts(),
                Duration.ofSeconds(config.getWebsocket().getReconnectIntervalSeconds())
            )
                .doBeforeRetry(signal -> 
                    log.info("Reconnecting to WebSocket (attempt {})", signal.totalRetries() + 1)
                )
            );
    }
    
    /**
     * Send a channel subscription message on the WebSocket session.
     */
    private void subscribeToChannel(org.springframework.web.reactive.socket.WebSocketSession session, String channel) {
        try {
            String msg = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                    .put("channel", channel)
                    .put("msg_type", "join")
            );
            session.send(Mono.just(session.textMessage(msg)))
                .doOnSuccess(_ -> log.info("Subscribed to market data channel: {}", channel))
                .doOnError(e -> log.error("Failed to subscribe to channel {}", channel, e))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to create subscription message for channel {}", channel, e);
        }
    }
    
    /**
     * Route an incoming WebSocket message to the correct handler based on channel.
     * Messages arrive as JSON arrays: {@code ["channel-name", {...payload...}]}
     */
    void routeMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            
            if (root.isArray() && root.size() >= 2) {
                String channel = root.get(0).asText();
                JsonNode payload = root.get(1);
                
                switch (channel) {
                    case "flow-alerts" -> handleFlowAlert(payload);
                    case "off_lit_trades" -> handleOffLitTrade(payload);
                    case "news" -> handleNewsHeadline(payload);
                    default -> {
                        if (channel.startsWith("gex_strike_expiry:")) {
                            handleSpotGexStream(message, channel);
                        } else if (channel.startsWith("price:")) {
                            handlePriceTick(payload, channel);
                        } else {
                            log.trace("Ignoring message from channel: {}", channel);
                        }
                    }
                }
                return;
            }
            
            // Handle non-array formats (error messages, legacy formats)
            if (root.has("error")) {
                log.warn("API error message: {}", root.path("error").asText());
                return;
            }
            
            // Legacy format with type field
            String type = root.path("type").asText();
            if ("flow-alert".equals(type) || "flow_alert".equals(type)) {
                JsonNode dataNode = root.path("data");
                if (!dataNode.isMissingNode()) {
                    handleFlowAlert(dataNode);
                }
            } else if (root.has("ticker") && root.has("strike")) {
                // Raw flow alert without wrapper
                handleFlowAlert(root);
            } else {
                log.trace("Received unrecognized message type: {}", type);
            }
        } catch (Exception e) {
            log.debug("Could not parse WS message: {}", message.substring(0, Math.min(100, message.length())));
        }
    }
    
    /**
     * Parse and dispatch a flow-alerts payload.
     */
    private void handleFlowAlert(JsonNode payload) {
        try {
            if (!payload.isObject() || !payload.has("ticker")) return;
            
            UnusualWhalesFlowAlertDto alert = objectMapper.treeToValue(payload, UnusualWhalesFlowAlertDto.class);
            flowAlertSink.tryEmitNext(alert);
            if (flowAlertHandler != null) {
                flowAlertHandler.accept(alert);
            }
            log.debug("Received flow alert for {} strike {} expiry {}", alert.ticker(), alert.strike(), alert.expiry());
        } catch (Exception e) {
            log.debug("Failed to parse flow-alert payload: {}", e.getMessage());
        }
    }
    
    /**
     * Parse and dispatch an off_lit_trades payload.
     */
    private void handleOffLitTrade(JsonNode payload) {
        try {
            if (!payload.isObject() || !payload.has("ticker")) return;
            
            DarkPoolRecentTradeDto trade = objectMapper.treeToValue(payload, DarkPoolRecentTradeDto.class);
            offLitTradeSink.tryEmitNext(trade);
            if (offLitTradeHandler != null) {
                offLitTradeHandler.accept(trade);
            }
            log.debug("Received off-lit trade: {} size={} premium={}", trade.ticker(), trade.size(), trade.premium());
        } catch (Exception e) {
            log.debug("Failed to parse off_lit_trades payload: {}", e.getMessage());
        }
    }

    /**
     * Parse and dispatch a news payload.
     */
    private void handleNewsHeadline(JsonNode payload) {
        try {
            if (!payload.isObject()) return;

            String headline = firstText(payload, "headline", "title");
            if (headline == null || headline.isBlank()) return;

            NewsHeadlineDto newsHeadline = new NewsHeadlineDto(
                parseCreatedAt(payload.path("created_at").asText(null)),
                headline,
                firstText(payload, "source", "provider"),
                stringList(payload.path("tickers")),
                textOrNull(payload.path("sentiment")),
                booleanOrNull(payload.path("is_major")),
                stringList(payload.path("tags")),
                objectMap(payload.path("meta")),
                textOrNull(payload.path("description")),
                textOrNull(payload.path("url")),
                textOrNull(payload.path("image_url"))
            );
            newsHeadlineSink.tryEmitNext(newsHeadline);
            if (newsHeadlineHandler != null) {
                newsHeadlineHandler.accept(newsHeadline);
            }
            log.debug("Received news headline from {}: {}", newsHeadline.source(), newsHeadline.headline());
        } catch (Exception e) {
            log.debug("Failed to parse news payload: {}", e.getMessage());
        }
    }

    private void handleSpotGexStream(String message, String channel) {
        if (!desiredSpotGexChannels.contains(channel)) {
            log.trace("Ignoring unsubscribed Spot GEX channel: {}", channel);
            return;
        }

        spotGexPayloadParser.parseEnvelope(message, Instant.now()).ifPresent(event -> {
            spotGexStreamSink.tryEmitNext(event);
            if (spotGexStreamHandler != null) {
                spotGexStreamHandler.accept(event);
            }
            log.debug("Received Spot GEX stream row for {} expiry {} strike {}",
                event.ticker(), event.expiry(), event.strikePrice());
        });
    }
    
    /**
     * Parse and dispatch a {@code price:<TICKER>} tick.
     * Payload format: {@code {"close":"562.82","time":1726670327692,"vol":6015555}}
     */
    private void handlePriceTick(JsonNode payload, String channel) {
        if (!desiredPriceChannels.contains(channel)) {
            log.trace("Ignoring unsubscribed price channel: {}", channel);
            return;
        }
        try {
            if (!payload.isObject()) return;

            String ticker = channel.substring("price:".length());
            String close = textOrNull(payload.path("close"));
            JsonNode time = payload.path("time");
            if (ticker.isBlank() || close == null || !time.isNumber()) return;

            UwPriceTickDto tick = new UwPriceTickDto(
                ticker.toUpperCase(Locale.ROOT),
                new BigDecimal(close),
                time.asLong(),
                payload.path("vol").isNumber() ? payload.path("vol").asLong() : null
            );
            if (priceTickHandler != null) {
                priceTickHandler.accept(tick);
            }
            log.trace("Received price tick for {}: {}", tick.ticker(), tick.close());
        } catch (Exception e) {
            log.debug("Failed to parse price payload on {}: {}", channel, e.getMessage());
        }
    }

    /**
     * Subscribe to flow alerts stream.
     */
    public Flux<UnusualWhalesFlowAlertDto> getFlowAlerts() {
        return flowAlertSink.asFlux();
    }
    
    /**
     * Subscribe to off-lit (dark pool) trades stream.
     */
    public Flux<DarkPoolRecentTradeDto> getOffLitTrades() {
        return offLitTradeSink.asFlux();
    }

    /**
     * Subscribe to market data news headlines.
     */
    public Flux<NewsHeadlineDto> getNewsHeadlines() {
        return newsHeadlineSink.asFlux();
    }

    /**
     * Subscribe to parsed Spot GEX stream rows.
     */
    public Flux<SpotGexStreamEvent> getSpotGexStreamEvents() {
        return spotGexStreamSink.asFlux();
    }
    
    /**
     * Register a handler for flow alerts.
     */
    public void onFlowAlert(Consumer<UnusualWhalesFlowAlertDto> handler) {
        this.flowAlertHandler = handler;
    }
    
    /**
     * Register a handler for off-lit trades.
     */
    public void onOffLitTrade(Consumer<DarkPoolRecentTradeDto> handler) {
        this.offLitTradeHandler = handler;
    }

    /**
     * Register a handler for market data news headlines.
     */
    public void onNewsHeadline(Consumer<NewsHeadlineDto> handler) {
        this.newsHeadlineHandler = handler;
    }

    /**
     * Register a handler for parsed Spot GEX stream rows.
     */
    public void onSpotGexStreamEvent(Consumer<SpotGexStreamEvent> handler) {
        this.spotGexStreamHandler = handler;
    }

    /**
     * Register a handler for {@code price:<TICKER>} ticks.
     */
    public void onPriceTick(Consumer<UwPriceTickDto> handler) {
        this.priceTickHandler = handler;
    }

    /**
     * Track a desired {@code price:<TICKER>} subscription. If a session is active,
     * send the join message immediately; otherwise it is replayed on reconnect.
     */
    public void subscribePriceTicker(String ticker) {
        String channel = priceChannel(ticker);
        desiredPriceChannels.add(channel);

        WebSocketSession session = activeSession;
        if (session != null && isConnected.get()) {
            subscribeToChannel(session, channel);
        }
    }

    /**
     * Stop routing a {@code price:<TICKER>} channel. Upstream leave support is not
     * assumed here; removed channels are ignored until a controlled reconnect.
     */
    public void unsubscribePriceTicker(String ticker) {
        desiredPriceChannels.remove(priceChannel(ticker));
    }

    /**
     * Track a desired ticker-scoped Spot GEX subscription. If a session is active,
     * send the join message immediately; otherwise it is replayed on reconnect.
     */
    public void subscribeSpotGexTicker(String ticker) {
        String channel = spotGexChannel(ticker);
        desiredSpotGexChannels.add(channel);

        WebSocketSession session = activeSession;
        if (session != null && isConnected.get()) {
            subscribeToChannel(session, channel);
        }
    }

    /**
     * Stop routing a ticker-scoped Spot GEX channel. Upstream leave support is not
     * assumed here; removed channels are ignored until a controlled reconnect.
     */
    public void unsubscribeSpotGexTicker(String ticker) {
        desiredSpotGexChannels.remove(spotGexChannel(ticker));
    }
    
    /**
     * Check if WebSocket is connected.
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    boolean shouldSubscribeToNews() {
        return config.getApi().getEndpoints().isNews()
            && config.getWebsocket().getNews().isEnabled();
    }

    private String spotGexChannel(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return "gex_strike_expiry:" + ticker.trim().toUpperCase(Locale.ROOT);
    }

    private String priceChannel(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return "price:" + ticker.trim().toUpperCase(Locale.ROOT);
    }

    private OffsetDateTime parseCreatedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(raw);
    }

    private String firstText(JsonNode payload, String firstField, String secondField) {
        String first = textOrNull(payload.path(firstField));
        return first == null ? textOrNull(payload.path(secondField)) : first;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Boolean booleanOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asBoolean();
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(node, new TypeReference<>() {});
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Collections.emptyMap();
        }
        return objectMapper.convertValue(node, STRING_OBJECT_MAP);
    }
}
