package com.abhinavmehta.confx.sdk.store;

import com.abhinavmehta.confx.sdk.dto.ConfigVersionDto;
import java.util.Collection;
import java.util.Optional;

public interface ConfigCache {
    void initializeCache(Collection<ConfigVersionDto> configs);
    void updateConfig(ConfigVersionDto configVersionDto);
    Optional<ConfigVersionDto> getConfig(String configKey);
    void removeConfig(String configKey);
    void clearCacheForEnvironment(Long environmentId); // If SDK is used for specific environmentId
    void clearCacheForProject(Long projectId); // If SDK is used for specific projectId
    void clearAll();
    Collection<ConfigVersionDto> getAllConfigs();
} 