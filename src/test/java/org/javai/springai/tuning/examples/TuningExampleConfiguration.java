package org.javai.springai.tuning.examples;

import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for tuning examples.
 * This configuration is conditionally activated when tuning examples are enabled.
 * 
 * Note: This configuration is in the test source set to colocate with the test scenarios
 * that implement ScenarioPlanSupplier. When tuning examples are enabled (via property),
 * this configuration becomes active and registers scenario beans.
 */
@Configuration
@ConditionalOnProperty(name = "app.tuning-example.enabled", havingValue = "true")
public class TuningExampleConfiguration {

	/**
	 * Register the StarSchemaQueryTest scenario as a bean for dependency injection.
	 * Directly instantiates the test class, which is available in the test source set.
	 */
	@Bean
	public ScenarioPlanSupplier starSchemaQueryTest() {
		try {
			Class<?> testClass = Class.forName("org.javai.springai.scenarios.StarSchemaQueryTest");
			return (ScenarioPlanSupplier) testClass.getConstructor().newInstance();
		} catch (Exception e) {
			throw new IllegalStateException(
					"Failed to load StarSchemaQueryTest. This example requires test classes to be available. " +
					"Ensure you run this with: ./gradlew bootRun --args='--app.tuning-example.enabled=true'",
					e);
		}
	}
}

