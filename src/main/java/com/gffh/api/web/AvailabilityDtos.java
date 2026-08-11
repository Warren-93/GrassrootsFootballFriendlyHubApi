package com.gffh.api.web;

import com.gffh.api.domain.AvailabilitySlot;
import com.gffh.api.domain.HomeAwayPreference;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public final class AvailabilityDtos {

    private AvailabilityDtos() {}

    public record CreateSlotRequest(
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull HomeAwayPreference homeAwayPreference,
            String venueId,
            String format,
            String notes) {}

    public record SlotView(
            String id, String teamId, LocalDate date, LocalTime startTime, LocalTime endTime,
            String homeAwayPreference, String venueId, String format, String notes, String status) {

        public static SlotView from(AvailabilitySlot s) {
            return new SlotView(s.id(), s.teamId(), s.date(), s.startTime(), s.endTime(),
                    s.homeAwayPreference().name(), s.venueId(),
                    s.format() == null ? null : s.format().name(), s.notes(), s.status().name());
        }
    }
}
