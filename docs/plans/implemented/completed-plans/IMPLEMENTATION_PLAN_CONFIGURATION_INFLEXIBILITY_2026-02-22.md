# Implementation Plan: Configuration Inflexibility

## Description
The current `ApplicationStartup.java` manually maps JSON configuration properties to the `AppConfig` builder using a chain of `applyInt` and `applyDouble` calls. Adding new configuration variables requires modifying the JSON, `AppConfig`, and the `applyJsonConfig` mapping logic, violating the Open-Closed principle and adding unnecessary maintenance overhead.

This plan details how to leverage Jackson's native databinding to map the JSON directly to `AppConfig.Builder` or `AppConfig` automatically.

## Proposed Changes

### 1. Update `ApplicationStartup.java`
- Replace the manually written `applyJsonConfig` implementation with `ObjectMapper.readerForUpdating(builder).readValue(json)`.
- If using `ObjectMapper.readValue`, we may need to configure the `ObjectMapper` to ignore unknown properties (`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`, false) to ensure backward compatibility.

#### [MODIFY] `ApplicationStartup.java`(file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/bootstrap/ApplicationStartup.java)
- Rework `applyJsonConfig(builder, json)` to execute `MAPPER.readerForUpdating(builder).readValue(json);`.
- Remove the private `applyInt(...)` and `applyDouble(...)` utility methods if they are no longer used anywhere else (though env mappings might still need them, so check `applyEnvInt`).

### 2. Update `AppConfig.java` (If necessary)
- Ensure Jackson can construct `AppConfig` or serialize directly to the Builder class. If the Builder methods do not use the standard `setXxx` naming convention, we may need to add `@JsonSetter("propertyName")` annotations to the builder methods or `@JsonPOJOBuilder(withPrefix = "")` on the Builder class.

## Verification Plan

### Automated Tests
- Run existing configuration tests to ensure JSON loading remains fully backward compatible:
  `mvn test -Dtest=ConfigLoaderTest`
- Run the full test suite to guarantee no missing or misconfigured app settings ripple into failing services:
  `mvn test`

### Manual Verification
- Manually change a value in `config/app-config.json` (e.g., `dailyLikeLimit`) and launch the CLI or UI to verify the value loaded successfully using `AppConfig.defaults().matching().dailyLikeLimit()`.
