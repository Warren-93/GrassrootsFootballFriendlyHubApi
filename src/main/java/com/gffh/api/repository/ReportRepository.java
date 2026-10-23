package com.gffh.api.repository;

import com.gffh.api.model.Report;
import com.gffh.api.model.enums.ReportSeverity;
import com.gffh.api.model.enums.ReportStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Comparator;
import java.util.List;

public interface ReportRepository extends MongoRepository<Report, String> {

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    /** Safeguarding-severity reports first, then newest first - ADM-04's queue ordering rule. */
    default List<Report> findByStatus(ReportStatus status) {
        return findByStatusOrderByCreatedAtDesc(status).stream()
                .sorted(Comparator.comparing((Report r) -> r.getSeverity() != ReportSeverity.SAFEGUARDING))
                .toList();
    }
}
