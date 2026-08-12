package com.gffh.api.repository;

import com.gffh.api.domain.Report;
import com.gffh.api.domain.ReportStatus;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {

    Optional<Report> findById(String id);

    /** Safeguarding-severity reports first, then newest first - ADM-04's queue ordering rule. */
    List<Report> findByStatus(ReportStatus status);

    Report save(Report report);
}
