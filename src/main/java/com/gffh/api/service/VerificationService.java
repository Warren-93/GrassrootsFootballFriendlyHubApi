package com.gffh.api.service;

import com.gffh.api.domain.Team;
import com.gffh.api.domain.VerificationRequest;
import com.gffh.api.domain.VerificationRequestStatus;
import com.gffh.api.domain.VerificationStatus;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.VerificationRequestRepository;
import com.gffh.api.web.VerificationDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * SCR-PR-07's submission and ADM-03's review queue - previously "verification
 * is currently flipped directly in the database for testing" per the mobile
 * README; this is the real workflow that replaces that.
 */
@Service
public class VerificationService {

    private final VerificationRequestRepository requests;
    private final TeamRepository teams;
    private final MembershipService membershipService;
    private final AuditLogService auditLogService;

    public VerificationService(VerificationRequestRepository requests, TeamRepository teams,
                               MembershipService membershipService, AuditLogService auditLogService) {
        this.requests = requests;
        this.teams = teams;
        this.membershipService = membershipService;
        this.auditLogService = auditLogService;
    }

    public VerificationDtos.VerificationRequestView submit(String userId, String teamId,
                                                            VerificationDtos.SubmitRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        if (requests.findPendingByTeamId(teamId).isPresent()) {
            throw new BusinessRuleException("VERIFICATION_ALREADY_PENDING", HttpStatus.CONFLICT,
                    "This team already has a verification request in progress.");
        }
        Team team = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
        teams.save(withVerification(team, VerificationStatus.PENDING));

        VerificationRequest saved = requests.save(new VerificationRequest(null, teamId, userId,
                request.affiliationNumber(), request.contactDetails(), request.evidenceUrls(),
                VerificationRequestStatus.PENDING, null, null, null, null, Instant.now(), null));
        return VerificationDtos.VerificationRequestView.from(saved);
    }

    /** The team's own manager checking where their most recent submission stands - null if never submitted. */
    public VerificationDtos.VerificationRequestView getForTeam(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return requests.findLatestByTeamId(teamId).map(VerificationDtos.VerificationRequestView::from).orElse(null);
    }

    public List<VerificationDtos.VerificationRequestView> queue() {
        return requests.findByStatus(VerificationRequestStatus.PENDING).stream()
                .map(VerificationDtos.VerificationRequestView::from).toList();
    }

    public VerificationDtos.VerificationRequestView get(String id) {
        return VerificationDtos.VerificationRequestView.from(find(id));
    }

    public VerificationDtos.VerificationRequestView approve(String adminId, String id) {
        VerificationRequest current = find(id);
        if (current.status() != VerificationRequestStatus.PENDING) {
            throw new BusinessRuleException("VERIFICATION_NOT_PENDING", HttpStatus.CONFLICT,
                    "This request has already been reviewed.");
        }
        Team team = teams.findById(current.teamId()).orElseThrow(BusinessRuleException::blocked);
        teams.save(withVerification(team, VerificationStatus.VERIFIED));

        VerificationRequest updated = new VerificationRequest(current.id(), current.teamId(),
                current.submittedByUserId(), current.affiliationNumber(), current.contactDetails(),
                current.evidenceUrls(), VerificationRequestStatus.APPROVED, null, null, null,
                adminId, current.submittedAt(), Instant.now());
        requests.save(updated);
        auditLogService.record(adminId, "VERIFICATION_APPROVED", "TEAM", current.teamId(), null);
        return VerificationDtos.VerificationRequestView.from(updated);
    }

    /**
     * First reject moves to AWAITING_SECOND_REJECTION; a second reject by a
     * *different* admin finalizes it to REJECTED and the reason is sent to
     * the applicant verbatim (ADM-03's two-person review rule).
     */
    public VerificationDtos.VerificationRequestView reject(String adminId, String id,
                                                            VerificationDtos.RejectRequest request) {
        VerificationRequest current = find(id);

        if (current.status() == VerificationRequestStatus.PENDING) {
            VerificationRequest updated = new VerificationRequest(current.id(), current.teamId(),
                    current.submittedByUserId(), current.affiliationNumber(), current.contactDetails(),
                    current.evidenceUrls(), VerificationRequestStatus.AWAITING_SECOND_REJECTION,
                    adminId, request.reason(), null, null, current.submittedAt(), null);
            requests.save(updated);
            auditLogService.record(adminId, "VERIFICATION_REJECT_FIRST_REVIEW", "TEAM", current.teamId(), request.reason());
            return VerificationDtos.VerificationRequestView.from(updated);
        }

        if (current.status() == VerificationRequestStatus.AWAITING_SECOND_REJECTION) {
            if (adminId.equals(current.firstRejectionAdminId())) {
                throw new BusinessRuleException("SAME_ADMIN_SECOND_REVIEW", HttpStatus.CONFLICT,
                        "A different administrator must confirm this rejection.");
            }
            Team team = teams.findById(current.teamId()).orElseThrow(BusinessRuleException::blocked);
            teams.save(withVerification(team, VerificationStatus.REJECTED));

            VerificationRequest updated = new VerificationRequest(current.id(), current.teamId(),
                    current.submittedByUserId(), current.affiliationNumber(), current.contactDetails(),
                    current.evidenceUrls(), VerificationRequestStatus.REJECTED,
                    current.firstRejectionAdminId(), current.firstRejectionReason(), request.reason(),
                    adminId, current.submittedAt(), Instant.now());
            requests.save(updated);
            auditLogService.record(adminId, "VERIFICATION_REJECTED", "TEAM", current.teamId(), request.reason());
            return VerificationDtos.VerificationRequestView.from(updated);
        }

        throw new BusinessRuleException("VERIFICATION_NOT_PENDING", HttpStatus.CONFLICT,
                "This request has already been reviewed.");
    }

    private VerificationRequest find(String id) {
        return requests.findById(id).orElseThrow(() -> new BusinessRuleException(
                "VERIFICATION_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "That request could not be found."));
    }

    private static Team withVerification(Team team, VerificationStatus status) {
        return new Team(team.id(), team.clubId(), team.name(), team.clubName(), team.badgeUrl(),
                team.ageGroup(), team.gender(), team.format(), team.abilityLevel(), team.league(),
                team.postcode(), team.location(), team.travelRadiusMiles(), team.homeAwayPreference(),
                team.managerName(), team.contactPhone(), team.description(), status, team.defaultVenueId(),
                team.firstFixtureConfirmedAt(), team.createdAt(), team.updatedAt(), team.suspended());
    }
}
