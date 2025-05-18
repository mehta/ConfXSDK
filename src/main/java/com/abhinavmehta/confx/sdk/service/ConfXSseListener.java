package com.abhinavmehta.confx.sdk.service;

import com.abhinavmehta.confx.sdk.ConfXSDKConfig;
import com.abhinavmehta.confx.sdk.dto.ConfigVersionDto;
import com.abhinavmehta.confx.sdk.dto.SseEventDto;
import com.abhinavmehta.confx.sdk.dto.DeletedConfigItemPayload;
import com.abhinavmehta.confx.sdk.dto.DeletedEnvironmentPayload;
import com.abhinavmehta.confx.sdk.dto.DeletedProjectPayload;
import com.abhinavmehta.confx.sdk.store.ConfigCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfXSseListener implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ConfXSseListener.class);
    private final ConfXSDKConfig config;
    private final ConfigCache configCache;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String sseUrl;
    private final AtomicInteger attemptCounter = new AtomicInteger(0);

    public ConfXSseListener(ConfXSDKConfig config, ConfigCache configCache, ObjectMapper objectMapper, ScheduledExecutorService executorService) {
        this.config = config;
        this.configCache = configCache;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getHttpClientTimeoutSeconds())) // For initial connection
                .build();
        this.sseUrl = String.format("%s/api/v1/stream/projects/%d/environments/%d",
                config.getServerUrl(), config.getProjectId(), config.getEnvironmentId());
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            attemptCounter.set(0);
            log.info("Starting SSE listener for URL: {}", sseUrl);
            executorService.submit(this); // submit to run in a background thread
        } else {
            log.warn("SSE listener is already running for URL: {}", sseUrl);
        }
    }

    public void stop() {
        log.info("Stopping SSE listener for URL: {}", sseUrl);
        running.set(false);
        // HttpClient's sendAsync might not be directly interruptible this way
        // but setting running to false will prevent reprocessing and retries.
        // The executor shutdown should handle thread termination if using a dedicated one.
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                log.info("Attempting to connect to SSE stream (attempt {}): {}", attemptCounter.incrementAndGet(), sseUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofDays(1)) // Effectively infinite for the stream itself once connected
                        .GET()
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofLines()) // Synchronous call within the executor thread
                    .lines()
                    .forEach(this::processSseLine);
                
                // If stream ends normally (e.g. server closes it deliberately)
                log.info("SSE stream ended for {}. Will attempt to reconnect if running flag is true.", sseUrl);
                if (running.get()) {
                    scheduleReconnect();
                }
                break; // Exit while loop, new run will be scheduled if needed

            } catch (IOException | InterruptedException e) {
                if (!running.get()) {
                    log.info("SSE listener for {} was stopped during an exception: {}", sseUrl, e.getMessage());
                    break; 
                }
                log.warn("Error in SSE stream for {}: {}. Will attempt to reconnect.", sseUrl, e.getMessage());
                scheduleReconnect();
                break; // Exit this run, reconnect will start a new one
            } catch (Exception e) {
                 if (!running.get()) {
                    log.info("SSE listener for {} was stopped during an unexpected exception: {}", sseUrl, e.getMessage());
                    break; 
                }
                log.error("Unexpected error in SSE listener for {}: {}. Will attempt to reconnect.", sseUrl, e.getMessage(), e);
                scheduleReconnect();
                break; // Exit this run, reconnect will start a new one
            }
        }
        log.info("SSE listener processing loop finished for {}. Running: {}", sseUrl, running.get());
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            log.info("Not scheduling SSE reconnect as listener is stopped for {}.", sseUrl);
            return;
        }
        long delay = config.getSseReconnectTimeMs();
        log.info("Scheduling SSE reconnect for {} in {} ms.", sseUrl, delay);
        executorService.schedule(this::start, delay, TimeUnit.MILLISECONDS);
    }

    private String currentEventName = null;
    private StringBuilder currentEventData = new StringBuilder();

    private void processSseLine(String line) {
        if (!running.get()) return;
        log.trace("SSE line received: {}", line);

        if (line.startsWith("event:")) {
            currentEventName = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
            currentEventData.append(line.substring("data:".length()).trim());
        } else if (line.startsWith(":")) { // Comment / heartbeat
            log.trace("SSE heartbeat/comment received: {}", line);
            // Reset attempt counter on successful heartbeat/comment as connection is alive
            attemptCounter.set(0);
        } else if (line.isEmpty()) { // Empty line signifies end of an event
            if (currentEventData.length() > 0) {
                handleSseEvent(currentEventName, currentEventData.toString());
            }
            currentEventName = null;
            currentEventData.setLength(0);
        } else {
            log.warn("Unrecognized SSE line: {}", line);
        }
    }

    private void handleSseEvent(String eventName, String eventData) {
        if (!running.get()) return;
        log.debug("Handling SSE event: Name='{}', Data='{}'", eventName, eventData);
        try {
            SseEventDto genericEvent = objectMapper.readValue(eventData, SseEventDto.class);
            String type = genericEvent.getType(); // Type from the JSON payload
            JsonNode payload = genericEvent.getPayload();

            if (ConfigUpdateSseDto.TYPE_CONFIG_VERSION_UPDATED.equals(type)) {
                ConfigVersionDto configDto = objectMapper.treeToValue(payload, ConfigVersionDto.class);
                configCache.updateConfig(configDto);
            } else if (ConfigUpdateSseDto.TYPE_CONFIG_ITEM_DELETED.equals(type)) {
                DeletedConfigItemPayload deletedPayload = objectMapper.treeToValue(payload, DeletedConfigItemPayload.class);
                configCache.removeConfig(deletedPayload.getConfigKey());
            } else if (ConfigUpdateSseDto.TYPE_ENVIRONMENT_DELETED.equals(type)) {
                // If SDK is for this specific environment, it should clear its cache and potentially stop.
                DeletedEnvironmentPayload deletedPayload = objectMapper.treeToValue(payload, DeletedEnvironmentPayload.class);
                if (config.getEnvironmentId().equals(deletedPayload.getEnvironmentId())) {
                    log.info("Monitored environment {} was deleted. Clearing cache and stopping SSE.", config.getEnvironmentId());
                    configCache.clearAll();
                    stop(); // Stop further processing for this environment
                }
            } else if (ConfigUpdateSseDto.TYPE_PROJECT_DELETED.equals(type)) {
                // If SDK is for this specific project, it should clear its cache and stop.
                DeletedProjectPayload deletedPayload = objectMapper.treeToValue(payload, DeletedProjectPayload.class);
                if (config.getProjectId().equals(deletedPayload.getProjectId())) {
                    log.info("Monitored project {} was deleted. Clearing cache and stopping SSE.", config.getProjectId());
                    configCache.clearAll();
                    stop(); // Stop further processing for this project
                }
            } else if ("connection_established".equals(eventName)) {
                log.info("SSE connection confirmed by server: {}", eventData);
                attemptCounter.set(0); // Reset connection attempts on successful establishment
            } else {
                log.warn("Received SSE event with unknown type/name: Name='{}', Type from data='{}'", eventName, type);
            }
        } catch (IOException e) {
            log.error("Failed to parse SSE event data: '{}'. Error: {}", eventData, e.getMessage(), e);
        }
    }
} 