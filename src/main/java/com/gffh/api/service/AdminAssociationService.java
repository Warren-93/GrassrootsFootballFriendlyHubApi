package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminAssociationDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.RegisteredOrganisation;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.repository.RegisteredOrganisationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The registered associations list, kept by admins (any admin, not only the
 * super admin). Nothing on the team side reads it yet - see
 * {@link RegisteredOrganisation}. Every change is audit logged.
 */
@Service
public class AdminAssociationService {

    private final RegisteredOrganisationRepository organisations;
    private final PlatformAdminRepository admins;
    private final AuditLogService auditLog;

    public AdminAssociationService(RegisteredOrganisationRepository organisations, PlatformAdminRepository admins,
                                   AuditLogService auditLog) {
        this.organisations = organisations;
        this.admins = admins;
        this.auditLog = auditLog;
    }

    public List<AdminAssociationDtos.OrganisationView> list() {
        return organisations.findAllByOrderByNameAsc().stream().map(AdminAssociationDtos.OrganisationView::from).toList();
    }

    public AdminAssociationDtos.OrganisationView create(String adminId, AdminAssociationDtos.OrganisationInput in) {
        requireUniqueName(in.name(), null);
        String email = admins.findById(adminId).map(PlatformAdmin::getEmail).orElse(null);
        RegisteredOrganisation saved = organisations.save(new RegisteredOrganisation(null, in.name().trim(),
                blankToNull(in.shortName()), in.linkedOrganisation(), blankToNull(in.contactName()),
                blankToNull(in.contactEmail()), blankToNull(in.website()), blankToNull(in.notes()), List.of(),
                null, email, null));
        auditLog.record(adminId, "ASSOCIATION_ADDED", "ASSOCIATION", saved.getId(), saved.getName());
        return AdminAssociationDtos.OrganisationView.from(saved);
    }

    public AdminAssociationDtos.OrganisationView update(String adminId, String id, AdminAssociationDtos.OrganisationInput in) {
        RegisteredOrganisation o = find(id);
        requireUniqueName(in.name(), id);
        RegisteredOrganisation saved = organisations.save(new RegisteredOrganisation(o.getId(), in.name().trim(),
                blankToNull(in.shortName()), in.linkedOrganisation(), blankToNull(in.contactName()),
                blankToNull(in.contactEmail()), blankToNull(in.website()), blankToNull(in.notes()), o.getLeagues(),
                o.getCreatedAt(), o.getCreatedByEmail(), null));
        auditLog.record(adminId, "ASSOCIATION_UPDATED", "ASSOCIATION", id, saved.getName());
        return AdminAssociationDtos.OrganisationView.from(saved);
    }

    public void delete(String adminId, String id) {
        RegisteredOrganisation o = find(id);
        organisations.deleteById(id);
        auditLog.record(adminId, "ASSOCIATION_REMOVED", "ASSOCIATION", id,
                o.getName() + " (" + o.getLeagues().size() + " league" + (o.getLeagues().size() == 1 ? "" : "s") + ")");
    }

    public AdminAssociationDtos.OrganisationView addLeague(String adminId, String id, AdminAssociationDtos.LeagueInput in) {
        RegisteredOrganisation o = find(id);
        requireUniqueLeague(o, in.name(), null);
        List<RegisteredOrganisation.League> leagues = new ArrayList<>(o.getLeagues());
        leagues.add(new RegisteredOrganisation.League(UUID.randomUUID().toString(), in.name().trim(), divisions(in)));
        auditLog.record(adminId, "LEAGUE_ADDED", "ASSOCIATION", id, o.getName() + ": " + in.name().trim());
        return save(o, leagues);
    }

    public AdminAssociationDtos.OrganisationView updateLeague(String adminId, String id, String leagueId,
                                                              AdminAssociationDtos.LeagueInput in) {
        RegisteredOrganisation o = find(id);
        findLeague(o, leagueId);
        requireUniqueLeague(o, in.name(), leagueId);
        List<RegisteredOrganisation.League> leagues = o.getLeagues().stream()
                .map(l -> l.id().equals(leagueId)
                        ? new RegisteredOrganisation.League(l.id(), in.name().trim(), divisions(in)) : l)
                .toList();
        auditLog.record(adminId, "LEAGUE_UPDATED", "ASSOCIATION", id, o.getName() + ": " + in.name().trim());
        return save(o, leagues);
    }

    public AdminAssociationDtos.OrganisationView deleteLeague(String adminId, String id, String leagueId) {
        RegisteredOrganisation o = find(id);
        RegisteredOrganisation.League league = findLeague(o, leagueId);
        List<RegisteredOrganisation.League> leagues = o.getLeagues().stream().filter(l -> !l.id().equals(leagueId)).toList();
        auditLog.record(adminId, "LEAGUE_REMOVED", "ASSOCIATION", id, o.getName() + ": " + league.name());
        return save(o, leagues);
    }

    private AdminAssociationDtos.OrganisationView save(RegisteredOrganisation o, List<RegisteredOrganisation.League> leagues) {
        return AdminAssociationDtos.OrganisationView.from(organisations.save(new RegisteredOrganisation(o.getId(), o.getName(),
                o.getShortName(), o.getLinkedOrganisation(), o.getContactName(), o.getContactEmail(), o.getWebsite(), o.getNotes(),
                leagues, o.getCreatedAt(), o.getCreatedByEmail(), null)));
    }

    private RegisteredOrganisation find(String id) {
        return organisations.findById(id).orElseThrow(() -> new BusinessRuleException(
                "ASSOCIATION_NOT_FOUND", HttpStatus.NOT_FOUND, "That association could not be found."));
    }

    private static RegisteredOrganisation.League findLeague(RegisteredOrganisation o, String leagueId) {
        return o.getLeagues().stream().filter(l -> l.id().equals(leagueId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("LEAGUE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "That league could not be found."));
    }

    private void requireUniqueName(String name, String exceptId) {
        organisations.findByName(name).filter(o -> !o.getId().equals(exceptId)).ifPresent(o -> {
            throw new BusinessRuleException("ASSOCIATION_EXISTS", HttpStatus.CONFLICT,
                    o.getName() + " is already on the list.");
        });
    }

    private static void requireUniqueLeague(RegisteredOrganisation o, String name, String exceptId) {
        boolean taken = o.getLeagues().stream()
                .anyMatch(l -> !l.id().equals(exceptId) && l.name().equalsIgnoreCase(name.trim()));
        if (taken) {
            throw new BusinessRuleException("LEAGUE_EXISTS", HttpStatus.CONFLICT,
                    o.getName() + " already has a league called " + name.trim() + ".");
        }
    }

    private static List<String> divisions(AdminAssociationDtos.LeagueInput in) {
        if (in.divisions() == null) return List.of();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String d : in.divisions()) {
            if (d != null && !d.isBlank()) unique.add(d.trim());
        }
        return List.copyOf(unique);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
