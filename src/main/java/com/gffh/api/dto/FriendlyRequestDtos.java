package com.gffh.api.dto;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import com.gffh.api.service.RequestStateMachine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class FriendlyRequestDtos {

    private FriendlyRequestDtos() {}

    public record SendRequest(
            @NotBlank String senderTeamId,
            @NotBlank String recipientTeamId,
            @NotBlank String senderSlotId,
            @NotBlank String recipientSlotId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            String venueId,
            @NotBlank String homeTeamId,
            @NotNull CostShare costShare,
            @NotNull RefereeArrangement refereeArrangement,
            String message) {}

    /** Contact details, populated only once {@link RequestStateMachine#disclosesContactDetails} is true. */
    public record TeamContact(String teamId, String teamName, String managerName, String contactPhone,
                              String venueId) {

        public static TeamContact from(Team t) {
            return new TeamContact(t.getId(), t.getName(), t.getManagerName(), t.getContactPhone(), t.getDefaultVenueId());
        }
    }

    /** Who is on each side of a request, so clients can show names and badges without another lookup. */
    public record TeamRef(String id, String name, String clubName, String badgeUrl) {

        public static TeamRef from(Team t) {
            return new TeamRef(t.getId(), t.getName(), t.getClubName(), t.getBadgeUrl());
        }
    }

    /**
     * Body for {@code POST /{id}/actions/{action}} - {@code reason} is optional
     * and free-text. {@code proposedStartTime}/{@code proposedEndTime}/
     * {@code proposedVenueId} are only meaningful on {@code suggestChanges}
     * (date isn't proposable - see {@link FriendlyRequest}'s javadoc on why)
     * and are ignored by every other action.
     */
    public record ActionRequest(
            String reason, LocalTime proposedStartTime, LocalTime proposedEndTime, String proposedVenueId) {}

    /**
     * {@code venue} is the currently agreed venue ({@code venueId}) and
     * {@code proposedVenue} the one a pending change proposes; either may be
     * null. {@code senderTeam}/{@code recipientTeam} are always populated
     * while the team exists.
     */
    public record FriendlyRequestView(
            String id, String senderTeamId, String recipientTeamId, String status,
            LocalDate date, LocalTime startTime, LocalTime endTime, String venueId,
            String homeTeamId, String awayTeamId, String costShare, String refereeArrangement,
            String message, String actionReason, List<String> availableActions,
            TeamContact senderContact, TeamContact recipientContact,
            LocalTime proposedStartTime, LocalTime proposedEndTime, String proposedVenueId,
            VenueDtos.VenueSummary venue, VenueDtos.VenueSummary proposedVenue,
            TeamRef senderTeam, TeamRef recipientTeam) {

        public static FriendlyRequestView from(FriendlyRequest r, boolean isSender,
                                               Team sender, Team recipient, Venue venue, Venue proposedVenue) {
            List<String> actions = RequestStateMachine.transitionsFor(r.getStatus(), isSender).stream()
                    .map(RequestStateMachine.Transition::action)
                    .toList();
            boolean disclose = RequestStateMachine.disclosesContactDetails(r.getStatus());
            return new FriendlyRequestView(
                    r.getId(), r.getSenderTeamId(), r.getRecipientTeamId(), r.getStatus().name(),
                    r.getDate(), r.getStartTime(), r.getEndTime(), r.getVenueId(), r.getHomeTeamId(), r.awayTeamId(),
                    r.getCostShare().name(), r.getRefereeArrangement().name(), r.getMessage(), r.getActionReason(), actions,
                    disclose && sender != null ? TeamContact.from(sender) : null,
                    disclose && recipient != null ? TeamContact.from(recipient) : null,
                    r.getProposedStartTime(), r.getProposedEndTime(), r.getProposedVenueId(),
                    VenueDtos.VenueSummary.from(venue), VenueDtos.VenueSummary.from(proposedVenue),
                    sender == null ? null : TeamRef.from(sender),
                    recipient == null ? null : TeamRef.from(recipient));
        }
    }
}
