package com.gffh.api.service;

import com.gffh.api.domain.Team;
import com.gffh.api.repository.VenueRepository;
import org.springframework.stereotype.Service;

/**
 * Whether a team can send and receive friendly requests: it must be verified
 * and have a venue. Venues are club-scoped, so a team counts as having one if
 * it has a default venue or its club has any venue at all.
 */
@Service
public class TeamReadinessService {

    private final VenueRepository venues;

    public TeamReadinessService(VenueRepository venues) {
        this.venues = venues;
    }

    public boolean hasVenue(Team team) {
        if (team.defaultVenueId() != null) return true;
        return team.clubId() != null && !venues.findByClubId(team.clubId()).isEmpty();
    }

    public boolean isReady(Team team) {
        return team.readyForRequests(hasVenue(team));
    }

    /** Throws the documented error naming what the sending team still needs. */
    public void requireCanSend(Team sender) {
        boolean hasVenue = hasVenue(sender);
        if (!sender.readyForRequests(hasVenue)) {
            throw BusinessRuleException.teamNotReady(sender.isVerified(), hasVenue);
        }
    }

    public void requireCanReceive(Team recipient) {
        if (!isReady(recipient)) throw BusinessRuleException.recipientNotReady();
    }
}
