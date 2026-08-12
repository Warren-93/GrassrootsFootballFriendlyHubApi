package com.gffh.api.web;

import com.gffh.api.domain.*;
import com.gffh.api.request.RequestStateMachine;
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
    public record TeamContact(String teamId, String managerName, String contactPhone, String venueId) {}

    /** Body for {@code POST /{id}/actions/{action}} - {@code reason} is optional and free-text. */
    public record ActionRequest(String reason) {}

    public record FriendlyRequestView(
            String id, String senderTeamId, String recipientTeamId, String status,
            LocalDate date, LocalTime startTime, LocalTime endTime, String venueId,
            String homeTeamId, String awayTeamId, String costShare, String refereeArrangement,
            String message, String actionReason, List<String> availableActions,
            TeamContact senderContact, TeamContact recipientContact) {

        public static FriendlyRequestView from(FriendlyRequest r, boolean isSender,
                                               TeamContact senderContact, TeamContact recipientContact) {
            List<String> actions = RequestStateMachine.transitionsFor(r.status(), isSender).stream()
                    .map(RequestStateMachine.Transition::action)
                    .toList();
            boolean disclose = RequestStateMachine.disclosesContactDetails(r.status());
            return new FriendlyRequestView(
                    r.id(), r.senderTeamId(), r.recipientTeamId(), r.status().name(),
                    r.date(), r.startTime(), r.endTime(), r.venueId(), r.homeTeamId(), r.awayTeamId(),
                    r.costShare().name(), r.refereeArrangement().name(), r.message(), r.actionReason(), actions,
                    disclose ? senderContact : null, disclose ? recipientContact : null);
        }
    }
}
