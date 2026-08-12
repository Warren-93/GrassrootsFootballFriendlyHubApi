package com.gffh.api.web;

import java.util.Map;

public final class AdminOverviewDtos {

    private AdminOverviewDtos() {}

    /**
     * {@code errorRatePercent} is a placeholder (always 0) - there's no
     * request-level error-rate metric wired up yet. Micrometer/Actuator are
     * already on the classpath (see IndexConfig's Mongo command listener),
     * so a real implementation would query its HTTP server metrics rather
     * than add a new tracking mechanism from scratch.
     */
    public record OverviewResponse(
            long verificationBacklog,
            Map<String, Long> reportQueueDepthBySeverity,
            long teamsCreatedThisWeek,
            long fixturesCreatedThisWeek,
            double errorRatePercent) {}
}
