package com.gffh.api.service;

import com.gffh.api.domain.MatchingWeightsVersion;
import com.gffh.api.matching.MatchingWeights;
import com.gffh.api.repository.MatchingWeightsVersionRepository;
import com.gffh.api.web.MatchDtos;
import com.gffh.api.web.MatchingConfigDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** ADM-08: factor weight sliders, preview against a live search, publish with a version note. */
@Service
public class MatchingConfigService {

    private final MatchingWeightsVersionRepository versions;
    private final MatchingWeightsProvider weightsProvider;
    private final MatchingService matchingService;
    private final AuditLogService auditLogService;

    public MatchingConfigService(MatchingWeightsVersionRepository versions, MatchingWeightsProvider weightsProvider,
                                 MatchingService matchingService, AuditLogService auditLogService) {
        this.versions = versions;
        this.weightsProvider = weightsProvider;
        this.matchingService = matchingService;
        this.auditLogService = auditLogService;
    }

    public MatchingWeights current() {
        return weightsProvider.current();
    }

    public List<MatchingConfigDtos.VersionView> history() {
        return versions.listAll().stream().map(MatchingConfigDtos.VersionView::from).toList();
    }

    /** Scores a real search with the proposed weights, without touching the published configuration. */
    public MatchDtos.SearchResponse preview(MatchingConfigDtos.PreviewRequest request) {
        MatchingWeights proposed = toValidatedWeights(request.weights());
        MatchDtos.SearchRequest searchRequest = new MatchDtos.SearchRequest(
                request.teamId(), null, null, null, null, null, null, null, null, 20, null);
        return matchingService.previewForAdmin(searchRequest, proposed);
    }

    public MatchingConfigDtos.VersionView publish(String adminId, MatchingConfigDtos.PublishRequest request) {
        MatchingWeights weights = toValidatedWeights(request.weights());

        versions.deactivateAll();
        MatchingWeightsVersion saved = versions.save(new MatchingWeightsVersion(null, weights,
                request.versionNote(), adminId, Instant.now(), true));

        weightsProvider.publish(weights);
        auditLogService.record(adminId, "MATCHING_CONFIG_PUBLISHED", "MATCHING_CONFIG", saved.id(), request.versionNote());
        return MatchingConfigDtos.VersionView.from(saved);
    }

    private MatchingWeights toValidatedWeights(MatchingConfigDtos.WeightsInput input) {
        try {
            return input.toWeights();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("INVALID_MATCHING_WEIGHTS", HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
