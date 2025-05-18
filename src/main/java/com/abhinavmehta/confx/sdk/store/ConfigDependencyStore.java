package com.abhinavmehta.confx.sdk.store;

import com.abhinavmehta.confx.sdk.dto.ConfigDependencyDto;
import java.util.List;

public interface ConfigDependencyStore {
    void initialize(List<ConfigDependencyDto> dependencies);
    List<ConfigDependencyDto> getPrerequisitesFor(String dependentConfigKey);
    // Could add getDependentsOf(String prerequisiteConfigKey) if needed for other features
    void clear();
} 