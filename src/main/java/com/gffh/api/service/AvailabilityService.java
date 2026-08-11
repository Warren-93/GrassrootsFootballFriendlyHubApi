package com.gffh.api.service;

import com.gffh.api.domain.AvailabilitySlot;
import com.gffh.api.domain.AvailabilityStatus;
import com.gffh.api.domain.Format;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.web.AvailabilityDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AvailabilityService {

    private final AvailabilityRepository availability;
    private final MembershipService memberships;

    public AvailabilityService(AvailabilityRepository availability, MembershipService memberships) {
        this.availability = availability;
        this.memberships = memberships;
    }

    public AvailabilitySlot create(String userId, String teamId, AvailabilityDtos.CreateSlotRequest request) {
        memberships.requireCanManageTeam(userId, teamId);
        if (request.date().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("SLOT_IN_PAST", HttpStatus.BAD_REQUEST,
                    "Availability cannot be published for a date that has already passed.");
        }
        Format format = request.format() == null ? null : Format.valueOf(request.format());
        AvailabilitySlot slot = new AvailabilitySlot(null, teamId, request.date(),
                request.startTime(), request.endTime(), request.homeAwayPreference(),
                request.venueId(), format, request.notes(), AvailabilityStatus.AVAILABLE);
        return availability.save(slot);
    }

    public List<AvailabilitySlot> list(String userId, String teamId, LocalDate from, LocalDate to) {
        memberships.requireCanManageTeam(userId, teamId);
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusWeeks(8);
        return availability.findBookableForTeamBetween(teamId, start, end);
    }

    public AvailabilitySlot get(String userId, String teamId, String slotId) {
        memberships.requireCanManageTeam(userId, teamId);
        return findOwned(teamId, slotId);
    }

    public AvailabilitySlot update(String userId, String teamId, String slotId,
                                   AvailabilityDtos.CreateSlotRequest request) {
        memberships.requireCanManageTeam(userId, teamId);
        AvailabilitySlot current = findOwned(teamId, slotId);

        if (current.status() != AvailabilityStatus.AVAILABLE) {
            throw new BusinessRuleException("SLOT_NOT_EDITABLE", HttpStatus.CONFLICT,
                    "This slot is attached to a friendly request and cannot be edited directly.");
        }
        if (request.date().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("SLOT_IN_PAST", HttpStatus.BAD_REQUEST,
                    "Availability cannot be published for a date that has already passed.");
        }

        Format format = request.format() == null ? null : Format.valueOf(request.format());
        AvailabilitySlot updated = new AvailabilitySlot(current.id(), teamId, request.date(),
                request.startTime(), request.endTime(), request.homeAwayPreference(),
                request.venueId(), format, request.notes(), AvailabilityStatus.AVAILABLE);
        return availability.save(updated);
    }

    private AvailabilitySlot findOwned(String teamId, String slotId) {
        return availability.findById(slotId)
                .filter(s -> s.teamId().equals(teamId))
                .orElseThrow(() -> new BusinessRuleException("SLOT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "That availability slot could not be found."));
    }

    public void withdraw(String userId, String teamId, String slotId) {
        memberships.requireCanManageTeam(userId, teamId);
        AvailabilitySlot slot = findOwned(teamId, slotId);

        if (slot.status() != AvailabilityStatus.AVAILABLE) {
            // RESERVED/BOOKED slots are attached to a negotiation or fixture -
            // withdrawing here would silently break it. That must go through
            // the friendly-request cancellation flow instead.
            throw new BusinessRuleException("SLOT_NOT_WITHDRAWABLE", HttpStatus.CONFLICT,
                    "This slot is attached to a friendly request and cannot be withdrawn directly.");
        }

        availability.save(new AvailabilitySlot(slot.id(), slot.teamId(), slot.date(),
                slot.startTime(), slot.endTime(), slot.homeAwayPreference(), slot.venueId(),
                slot.format(), slot.notes(), AvailabilityStatus.WITHDRAWN));
    }
}
