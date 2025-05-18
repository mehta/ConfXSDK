package com.abhinavmehta.confx.sdk;

import com.abhinavmehta.confx.sdk.dto.ConfigDataType;
import com.abhinavmehta.confx.sdk.dto.ConfigVersionDto;
import com.abhinavmehta.confx.sdk.dto.EvaluationContext;
import com.abhinavmehta.confx.sdk.evaluator.ConfXConfigEvaluator;
import com.abhinavmehta.confx.sdk.service.ConfXHttpService;
import com.abhinavmehta.confx.sdk.service.ConfXSseListener;
// TODO: Import ConfigEvaluator service once created
import com.abhinavmehta.confx.sdk.store.ConfigCache;
import com.abhinavmehta.confx.sdk.store.ConfigDependencyStore;
import com.abhinavmehta.confx.sdk.store.InMemoryConfigCache;
import com.abhinavmehta.confx.sdk.store.InMemoryConfigDependencyStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfXClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ConfXClient.class);

    private final ConfXSDKConfig config;
    private final ConfigCache configCache;
    private final ConfigDependencyStore dependencyStore;
    private final ConfXHttpService httpService;
    private final ConfXSseListener sseListener;
    private final ConfXConfigEvaluator configEvaluator;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService internalExecutorService; // Used if no executor is provided in config
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ConfXClient(ConfXSDKConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        if (config.getExecutorService() != null) {
            this.internalExecutorService = null; // Indicates external executor is used
        } else {
            this.internalExecutorService = Executors.newScheduledThreadPool(3, r -> {
                Thread t = new Thread(r, "confx-sdk-pool-" + r.hashCode());
                t.setDaemon(true);
                return t;
            });
        }
        ScheduledExecutorService effectiveExecutor = getEffectiveExecutorService();

        this.configCache = new InMemoryConfigCache(config.getProjectId(), config.getEnvironmentId());
        this.dependencyStore = new InMemoryConfigDependencyStore(config.getProjectId());
        this.httpService = new ConfXHttpService(config, objectMapper);
        this.sseListener = new ConfXSseListener(config, configCache, objectMapper, effectiveExecutor);
        this.configEvaluator = new ConfXConfigEvaluator(this.configCache, this.dependencyStore, config.getProjectId(), config.getEnvironmentId(), this.objectMapper);
        
        initialize();
    }

    private ScheduledExecutorService getEffectiveExecutorService() {
        return config.getExecutorService() != null ? config.getExecutorService() : this.internalExecutorService;
    }

    private void initialize() {
        if (closed.get()) {
            log.warn("ConfXClient is closed, cannot initialize.");
            return;
        }
        log.info("Initializing ConfXClient for Project: {}, Environment: {}...", config.getProjectId(), config.getEnvironmentId());
        
        CompletableFuture<Void> configLoadFuture = loadInitialConfigsWithRetries();
        CompletableFuture<Void> depsLoadFuture = loadInitialDependenciesWithRetries();

        CompletableFuture.allOf(configLoadFuture, depsLoadFuture).thenRun(() -> {
            if (!closed.get()) {
                sseListener.start();
                initialized.set(true);
                log.info("ConfXClient initialized successfully (configs and dependencies loaded).");
            } else {
                 log.info("ConfXClient was closed during initialization.");
            }
        }).exceptionally(ex -> {
            log.error("ConfXClient failed to initialize (configs or dependencies): {}", ex.getMessage(), ex);
            return null;
        });
    }

    private CompletableFuture<Void> loadInitialConfigsWithRetries() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicInteger retries = new AtomicInteger(0);
        ScheduledExecutorService executor = getEffectiveExecutorService();

        Runnable tryLoadConfigs = () -> {
            if (closed.get()) {
                future.completeExceptionally(new IllegalStateException("SDK closed during initial config load."));
                return;
            }
            httpService.fetchAllActiveConfigs()
                .thenAccept(configs -> {
                    configCache.initializeCache(configs);
                    log.info("Successfully loaded initial {} configs.", configs != null ? configs.size() : 0);
                    future.complete(null);
                })
                .exceptionally(ex -> {
                    if (retries.incrementAndGet() <= config.getMaxRetries()) {
                        log.warn("Failed to load initial configs (attempt {}/{}). Retrying in {} ms. Error: {}",
                                 retries.get(), config.getMaxRetries(), config.getRetryDelayMs(), ex.getMessage());
                        executor.schedule(tryLoadConfigs, config.getRetryDelayMs(), TimeUnit.MILLISECONDS);
                    } else {
                        log.error("Failed to load initial configs after {} retries. Max retries reached. Error: {}",
                                  config.getMaxRetries(), ex.getMessage());
                        future.completeExceptionally(ex);
                    }
                    return null;
                });
        };
        executor.submit(tryLoadConfigs);
        return future;
    }

    private CompletableFuture<Void> loadInitialDependenciesWithRetries() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicInteger retries = new AtomicInteger(0);
        ScheduledExecutorService executor = getEffectiveExecutorService();

        Runnable tryLoadDeps = () -> {
            if (closed.get()) {
                future.completeExceptionally(new IllegalStateException("SDK closed during initial dependency load."));
                return;
            }
            httpService.fetchAllDependenciesForProject()
                .thenAccept(dependencies -> {
                    dependencyStore.initialize(dependencies);
                    log.info("Successfully loaded initial {} dependencies.", dependencies != null ? dependencies.size() : 0);
                    future.complete(null);
                })
                .exceptionally(ex -> {
                    if (retries.incrementAndGet() <= config.getMaxRetries()) {
                        log.warn("Failed to load initial dependencies (attempt {}/{}). Retrying in {} ms. Error: {}",
                                 retries.get(), config.getMaxRetries(), config.getRetryDelayMs(), ex.getMessage());
                        executor.schedule(tryLoadDeps, config.getRetryDelayMs(), TimeUnit.MILLISECONDS);
                    } else {
                        log.error("Failed to load initial dependencies after {} retries. Max retries reached. Error: {}",
                                  config.getMaxRetries(), ex.getMessage());
                        future.completeExceptionally(ex);
                    }
                    return null;
                });
        };
        executor.submit(tryLoadDeps);
        return future;
    }

    public boolean isInitialized() {
        return initialized.get();
    }
    
    // Actual config evaluation methods
    @SuppressWarnings("unchecked")
    private <T> T getConfigValue(String configKey, EvaluationContext context, Class<T> expectedType, T defaultValue) {
        if (!isInitialized()) {
            log.warn("ConfXClient not initialized for key '{}'. Returning default value.", configKey);
            return defaultValue;
        }
        if (closed.get()) {
            log.warn("ConfXClient is closed for key '{}'. Returning default value.", configKey);
            return defaultValue;
        }

        EvaluationContext evalContext = (context == null) ? EvaluationContext.builder().build() : context;

        try {
            ConfXConfigEvaluator.EvaluatedConfigResult result = configEvaluator.evaluate(configKey, evalContext);

            if ("NOT_FOUND".equals(result.evaluationSource)) {
                log.warn("Config key '{}' not found. Returning default value.", configKey);
                return defaultValue;
            }
            
            if (result.value == null && expectedType == Boolean.class) {
                 return (T) Boolean.FALSE; // Consistent with getOffValue
            }
            if (result.value == null) {
                return defaultValue; // Or null if T allows and defaultValue is null
            }

            if (expectedType.isAssignableFrom(result.value.getClass())) {
                return (T) result.value;
            } else {
                log.warn("Type mismatch for config key '{}'. Expected: {}, Actual: {}. Returning default value.", 
                         configKey, expectedType.getName(), result.value.getClass().getName());
                return defaultValue;
            }
        } catch (Exception e) {
            log.error("Error evaluating config key '{}': {}. Returning default value.", configKey, e.getMessage(), e);
            return defaultValue;
        }
    }

    public String getStringValue(String configKey, EvaluationContext context, String defaultValue) {
        return getConfigValue(configKey, context, String.class, defaultValue);
    }

    public Boolean getBooleanValue(String configKey, EvaluationContext context, Boolean defaultValue) {
        Boolean result = getConfigValue(configKey, context, Boolean.class, defaultValue);
        return result != null ? result : (defaultValue != null ? defaultValue : false); // Ensure boolean always returns boolean or specified default
    }

    public Integer getIntegerValue(String configKey, EvaluationContext context, Integer defaultValue) {
        return getConfigValue(configKey, context, Integer.class, defaultValue);
    }

    public Double getDoubleValue(String configKey, EvaluationContext context, Double defaultValue) {
        return getConfigValue(configKey, context, Double.class, defaultValue);
    }

    public JsonNode getJsonValue(String configKey, EvaluationContext context, JsonNode defaultValue) {
        return getConfigValue(configKey, context, JsonNode.class, defaultValue);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Closing ConfXClient for Project: {}, Environment: {}...", config.getProjectId(), config.getEnvironmentId());
            if (sseListener != null) {
                sseListener.stop();
            }
            if (internalExecutorService != null && !internalExecutorService.isShutdown()) {
                internalExecutorService.shutdown();
                try {
                    if (!internalExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        internalExecutorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    internalExecutorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("ConfXClient closed.");
        }
    }
} 