package com.abhinavmehta.confx.sdk;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class ConfXSDKConfig {
    @NonNull
    private String serverUrl; // Base URL of the ConfX server, e.g., "http://localhost:8080"
    @NonNull
    private Long projectId;
    @NonNull
    private Long environmentId;

    @Builder.Default
    private int maxRetries = 3; // Max retries for initial connection / fetching configs
    @Builder.Default
    private long retryDelayMs = 5000; // Delay between retries in milliseconds
    @Builder.Default
    private long sseReconnectTimeMs = 5000; // Time to wait before attempting SSE reconnect
    @Builder.Default
    private long httpClientTimeoutSeconds = 30; // Timeout for HTTP client requests

    // Custom executor for background tasks (SSE listener, retries). If null, SDK will create a default one.
    private java.util.concurrent.ScheduledExecutorService executorService;
} 