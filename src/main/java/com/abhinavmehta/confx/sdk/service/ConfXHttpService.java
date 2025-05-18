package com.abhinavmehta.confx.sdk.service;

import com.abhinavmehta.confx.sdk.ConfXSDKConfig;
import com.abhinavmehta.confx.sdk.dto.ConfigVersionDto;
import com.abhinavmehta.confx.sdk.dto.ConfigDependencyDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConfXHttpService {
    private static final Logger log = LoggerFactory.getLogger(ConfXHttpService.class);
    private final ConfXSDKConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ConfXHttpService(ConfXSDKConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getHttpClientTimeoutSeconds()))
                .build();
    }

    public CompletableFuture<List<ConfigVersionDto>> fetchAllActiveConfigs() {
        String url = String.format("%s/api/v1/projects/%d/environments/%d/all-active-configs",
                config.getServerUrl(), config.getProjectId(), config.getEnvironmentId());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getHttpClientTimeoutSeconds()))
                .GET()
                .build();

        log.info("Fetching all active configs from: {}", url);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<ConfigVersionDto>>() {});
                        } catch (IOException e) {
                            log.error("Failed to parse bulk config response from {}: {}", url, e.getMessage());
                            throw new RuntimeException("Failed to parse config response: " + e.getMessage(), e);
                        }
                    } else {
                        log.error("Failed to fetch bulk configs from {}. Status: {}, Body: {}", url, response.statusCode(), response.body());
                        throw new RuntimeException("Failed to fetch configs. Status: " + response.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    log.error("Exception during bulk config fetch from {}: {}", url, ex.getMessage(), ex);
                    throw new RuntimeException("Exception during config fetch: " + ex.getMessage(), ex);
                });
    }

    public CompletableFuture<List<ConfigDependencyDto>> fetchAllDependenciesForProject() {
        String url = String.format("%s/api/v1/projects/%d/dependencies/all",
                config.getServerUrl(), config.getProjectId());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getHttpClientTimeoutSeconds()))
                .GET()
                .build();

        log.info("Fetching all dependencies for project {} from: {}", config.getProjectId(), url);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<ConfigDependencyDto>>() {});
                        } catch (IOException e) {
                            log.error("Failed to parse dependencies response from {}: {}", url, e.getMessage());
                            throw new RuntimeException("Failed to parse dependencies response: " + e.getMessage(), e);
                        }
                    } else {
                        log.error("Failed to fetch dependencies from {}. Status: {}, Body: {}", url, response.statusCode(), response.body());
                        throw new RuntimeException("Failed to fetch dependencies. Status: " + response.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    log.error("Exception during dependencies fetch from {}: {}", url, ex.getMessage(), ex);
                    throw new RuntimeException("Exception during dependencies fetch: " + ex.getMessage(), ex);
                });
    }
} 