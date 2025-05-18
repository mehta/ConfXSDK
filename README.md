## Part 1: Java SDK Setup (Overview)

The ConfX project includes a Java SDK located in the `ConfXSDK` directory. This SDK allows Java applications (including other Spring Boot services) to integrate with the ConfX server to fetch configurations, evaluate them client-side, and receive real-time updates via Server-Sent Events (SSE).

### 1.1 Building the SDK

1.  Navigate to the SDK directory:
    ```bash
    cd ConfXSDK
    ```
2.  Build the SDK using Gradle:
    ```bash
    ./gradlew clean build
    ```
    This will compile the SDK and run its tests, producing a JAR file (e.g., `ConfXSDK/build/libs/confx-sdk-0.0.1-SNAPSHOT.jar`).

### 1.2 Using the SDK in a Client Spring Boot Application

1.  **Add as a Dependency:**
    In your client Spring Boot project, add the SDK JAR as a dependency. For local development, after building the SDK, you can install it to your local Maven repository by running `./gradlew publishToMavenLocal` in the `ConfXSDK` directory. Then, reference it in your client project's `build.gradle`:

    ```gradle
    dependencies {
        implementation 'com.abhinavmehta.confx:confx-sdk:0.0.1-SNAPSHOT'
        // ... other dependencies
    }
    ```
    If publishing to a shared repository (like Artifactory or Maven Central), use the appropriate coordinates.

2.  **Configure and Initialize `ConfXClient` as a Spring Bean:**
    Create a Spring `@Configuration` class in your client application to define the `ConfXClient` bean. This ensures the SDK is initialized when your application starts and its lifecycle is managed by Spring.

    ```java
    package com.example.myapp.config; // Your client application's package

    import com.abhinavmehta.confx.sdk.ConfXClient;
    import com.abhinavmehta.confx.sdk.ConfXSDKConfig;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

    import java.util.concurrent.ScheduledExecutorService;

    @Configuration
    public class ConfXClientConfiguration {

        @Value("${confx.server.url:http://localhost:8080}")
        private String confxServerUrl;

        @Value("${confx.project.id}") // Ensure these are in your application.properties or env
        private Long confxProjectId;

        @Value("${confx.environment.id}")
        private Long confxEnvironmentId;

        // Optional: Define a shared ScheduledExecutorService if you want more control
        // Otherwise, the SDK creates its own internal one.
        @Bean(name = "confXSdkExecutor")
        public ScheduledExecutorService confXSdkExecutor() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2); // Configure as needed
            scheduler.setThreadNamePrefix("confx-sdk-tasks-");
            scheduler.setDaemon(true);
            scheduler.initialize();
            return scheduler.getScheduledExecutor();
        }

        @Bean(destroyMethod = "close") // Spring will call client.close() on shutdown
        public ConfXClient confXClient(/* Optional: @Qualifier("confXSdkExecutor") ScheduledExecutorService executor */) {
            ConfXSDKConfig sdkConfig = ConfXSDKConfig.builder()
                    .serverUrl(confxServerUrl)
                    .projectId(confxProjectId)
                    .environmentId(confxEnvironmentId)
                    // .executorService(executor) // Optionally pass the shared executor
                    // .sseReconnectTimeMs(5000) 
                    // .maxRetries(5)
                    .build();
            
            // The ConfXClient constructor initiates asynchronous loading and SSE connection.
            return new ConfXClient(sdkConfig); 
        }
    }
    ```
    Ensure you have the necessary properties (e.g., `confx.project.id`, `confx.environment.id`, and optionally `confx.server.url`) in your client application's `application.properties` or environment variables.

3.  **Inject and Use `ConfXClient` in Your Services/Components:**
    Now you can inject the `ConfXClient` bean into any of your Spring components (services, controllers, repositories, etc.).

    **Example: `MyFeatureService.java`**
    ```java
    package com.example.myapp.service;

    import com.abhinavmehta.confx.sdk.ConfXClient;
    import com.abhinavmehta.confx.sdk.dto.EvaluationContext;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;
    import java.util.Map;

    @Service
    public class MyFeatureService {

        private final ConfXClient confXClient;

        @Autowired
        public MyFeatureService(ConfXClient confXClient) {
            this.confXClient = confXClient;
        }

        public boolean isSuperCheckoutEnabled(String userId) {
            if (confXClient.isInitialized()) {
                EvaluationContext context = EvaluationContext.builder()
                    .attributes(Map.of("userId", userId, "userTier", "gold"))
                    .build();
                return confXClient.getBooleanValue("tms.checkout.superCheckout.enabled", context, false);
            } else {
                // SDK not ready, return a safe default
                // Log a warning or error here if this state is unexpected in production
                return false; 
            }
        }
    }
    ```

    **Example: `AnotherBusinessLogic.java`**
    ```java
    package com.example.myapp.logic;

    import com.abhinavmehta.confx.sdk.ConfXClient;
    import com.abhinavmehta.confx.sdk.dto.EvaluationContext;
    import org.springframework.stereotype.Component;

    @Component
    public class AnotherBusinessLogic {

        private final ConfXClient confXClient;

        public AnotherBusinessLogic(ConfXClient confXClient) {
            this.confXClient = confXClient;
        }

        public String getShipmentNotificationMessage(String customerType) {
            if (!confXClient.isInitialized()) {
                return "Your shipment is on its way!"; // Safe default
            }
            EvaluationContext context = EvaluationContext.builder()
                .attributes(Map.of("customerType", customerType))
                .build();
            return confXClient.getStringValue("tms.notification.shipment.template", context, "Default shipment update.");
        }
    }
    ```

Detailed SDK usage, including evaluation context and specific value getters, is provided within the SDK's own documentation or examples (if available). The SDK handles in-memory caching, SSE updates, and client-side rule/dependency evaluation automatically once initialized.
