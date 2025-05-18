package com.abhinavmehta.confx.sdk.store;

import com.abhinavmehta.confx.sdk.dto.ConfigDependencyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryConfigDependencyStore implements ConfigDependencyStore {
    private static final Logger log = LoggerFactory.getLogger(InMemoryConfigDependencyStore.class);
    // Key: dependentConfigKey, Value: List of its prerequisites (ConfigDependencyDto)
    private final Map<String, List<ConfigDependencyDto>> dependencyMap = new ConcurrentHashMap<>();
    private final Long monitoredProjectId;

    public InMemoryConfigDependencyStore(Long projectId) {
        this.monitoredProjectId = projectId;
    }

    @Override
    public void initialize(List<ConfigDependencyDto> dependencies) {
        dependencyMap.clear();
        if (dependencies != null) {
            for (ConfigDependencyDto dep : dependencies) {
                // Assuming dependencies are for the monitored project.
                // Server endpoint ensures this for /projects/{projectId}/dependencies/all
                dependencyMap.computeIfAbsent(dep.getDependentConfigKey(), k -> new ArrayList<>()).add(dep);
            }
        }
        log.info("Dependency store initialized with {} dependency relations for project {}.", 
                 dependencyMap.values().stream().mapToInt(List::size).sum(), monitoredProjectId);
    }

    @Override
    public List<ConfigDependencyDto> getPrerequisitesFor(String dependentConfigKey) {
        return dependencyMap.getOrDefault(dependentConfigKey, Collections.emptyList());
    }

    @Override
    public void clear() {
        dependencyMap.clear();
        log.info("Dependency store cleared for project {}.", monitoredProjectId);
    }
} 