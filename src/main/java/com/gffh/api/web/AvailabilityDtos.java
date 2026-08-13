package com.gffh.api.web;

import com.gffh.api.domain.AvailabilitySlot;
import com.gffh.api.domain.HomeAwayPreference;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

    /** SCR-AV-04: one time window published across several dates at once - a Saturday slot repeated for eight weeks, say. */
    public record BulkCreateSlotRequest(
            @NotEmpty @Size(max = 52) List<@NotNull LocalDate> dates,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull HomeAwayPreference homeAwayPreference,
            String venueId,
            String format,
            String notes) {}

    public record BulkCreateResult(List<SlotView> created, List<LocalDate> skippedPastDates) {}

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
