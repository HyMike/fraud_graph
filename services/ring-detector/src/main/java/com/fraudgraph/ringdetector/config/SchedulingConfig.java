package com.fraudgraph.ringdetector.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Split out from the main application class so it can be turned off in tests
 * (see src/test/resources/application.yml): @Scheduled fires on context
 * startup, and every @SpringBootTest here loads the full app context, so the
 * periodic recompute would otherwise race a test's own recomputeClusters()
 * call against the same GDS in-memory graph name.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
