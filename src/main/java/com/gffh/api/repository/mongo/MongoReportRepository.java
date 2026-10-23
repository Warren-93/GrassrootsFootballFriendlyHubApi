package com.gffh.api.repository.mongo;

import com.gffh.api.model.Report;
import com.gffh.api.model.enums.ReportSeverity;
import com.gffh.api.model.enums.ReportStatus;
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
        Report doc = mongoTemplate.findById(id, Report.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<Report> findByStatus(ReportStatus status) {
        Query query = new Query(Criteria.where("status").is(status.name()))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Report> reports = mongoTemplate.find(query, Report.class, COLLECTION);
        // Safeguarding first, regardless of creation order.
        return reports.stream()
                .sorted(Comparator.comparing((Report r) -> r.getSeverity() != ReportSeverity.SAFEGUARDING))
                .toList();
    }

    @Override
    public Report save(Report report) {
        Report toSave = report.getId() != null ? report : withGeneratedId(report);
        Report doc = toSave;
        doc.setUpdatedAt(Instant.now());
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private Report withGeneratedId(Report r) {
        Instant now = Instant.now();
        return new Report(UUID.randomUUID().toString(), r.getReporterTeamId(), r.getReportedTeamId(),
                r.getRelatedFixtureId(), r.getType(), r.getSeverity(), r.getDetails(), r.getStatus(), r.getAssignedAdminId(),
                r.getInternalNotes(), now, now);
    }
}
