package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Report;
import com.gffh.api.domain.ReportSeverity;
import com.gffh.api.domain.ReportStatus;
import com.gffh.api.repository.ReportRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoReportRepository implements ReportRepository {

    private static final String COLLECTION = "reports";

    private final MongoTemplate mongoTemplate;

    public MongoReportRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Report> findById(String id) {
        ReportDocument doc = mongoTemplate.findById(id, ReportDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(ReportDocument::toDomain);
    }

    @Override
    public List<Report> findByStatus(ReportStatus status) {
        Query query = new Query(Criteria.where("status").is(status.name()))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Report> reports = mongoTemplate.find(query, ReportDocument.class, COLLECTION).stream()
                .map(ReportDocument::toDomain).toList();
        // Safeguarding first, regardless of creation order.
        return reports.stream()
                .sorted(Comparator.comparing((Report r) -> r.severity() != ReportSeverity.SAFEGUARDING))
                .toList();
    }

    @Override
    public Report save(Report report) {
        Report toSave = report.id() != null ? report : withGeneratedId(report);
        ReportDocument doc = ReportDocument.from(toSave);
        doc.updatedAt = Instant.now();
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Report withGeneratedId(Report r) {
        Instant now = Instant.now();
        return new Report(UUID.randomUUID().toString(), r.reporterTeamId(), r.reportedTeamId(),
                r.relatedFixtureId(), r.type(), r.severity(), r.status(), r.assignedAdminId(),
                r.internalNotes(), now, now);
    }
}
