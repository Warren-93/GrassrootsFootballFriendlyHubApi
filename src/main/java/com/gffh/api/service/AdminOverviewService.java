package com.gffh.api.service;

import com.gffh.api.domain.ReportSeverity;
import com.gffh.api.web.AdminOverviewDtos;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ADM-02's dashboard aggregation. Reads collections directly rather than
 * through each domain's repository interface: this is a cross-cutting
 * read-only rollup, not a use case any single repository should need to
 * anticipate.
 */
@Service
public class AdminOverviewService {

    private final MongoTemplate mongoTemplate;

    public AdminOverviewService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AdminOverviewDtos.OverviewResponse get() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long verificationBacklog = mongoTemplate.count(
                new Query(Criteria.where("status").in("PENDING", "AWAITING_SECOND_REJECTION")),
                "verification_requests");

        Map<String, Long> reportsBySeverity = new LinkedHashMap<>();
        for (ReportSeverity severity : ReportSeverity.values()) {
            long count = mongoTemplate.count(
                    new Query(Criteria.where("status").is("OPEN").and("severity").is(severity.name())),
                    "reports");
            reportsBySeverity.put(severity.name(), count);
        }

        long teamsThisWeek = mongoTemplate.count(
                new Query(Criteria.where("createdAt").gte(weekAgo)), "teams");
        long fixturesThisWeek = mongoTemplate.count(
                new Query(Criteria.where("createdAt").gte(weekAgo)), "fixtures");

        return new AdminOverviewDtos.OverviewResponse(
                verificationBacklog, reportsBySeverity, teamsThisWeek, fixturesThisWeek, 0.0);
    }
}
