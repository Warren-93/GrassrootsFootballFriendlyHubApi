package com.gffh.api.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A published window in which a team can play.
 *
 * <p>The window is a range, not a kick-off time: a proposed kick-off plus the
 * match duration must fit inside it. That is what makes overlap scoring
 * meaningful rather than a simple date equality check.
 */
public record AvailabilitySlot(
        String id,
        String teamId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        HomeAwayPreference homeAwayPreference,
        String venueId,
        Format format,
        String notes,
        AvailabilityStatus status) {

    public AvailabilitySlot {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("end time must be after start time");
        }
    }

    public int durationMinutes() {
        return (endTime.toSecondOfDay() - startTime.toSecondOfDay()) / 60;
    }

    public boolean isBookable() { return status == AvailabilityStatus.AVAILABLE; }

    /** Minutes of overlap with another slot; zero when the dates differ. */
    public int overlapMinutesWith(AvailabilitySlot other) {
        if (!date.equals(other.date)) return 0;
        int start = Math.max(startTime.toSecondOfDay(), other.startTime.toSecondOfDay());
        int end = Math.min(endTime.toSecondOfDay(), other.endTime.toSecondOfDay());
        return end <= start ? 0 : (end - start) / 60;
    }

    /** The overlapping window itself, for the timeline on SCR-FF-05 and SCR-FF-06. */
    public LocalTime overlapStartWith(AvailabilitySlot other) {
        return startTime.isAfter(other.startTime) ? startTime : other.startTime;
    }

    public boolean accommodates(LocalTime kickOff, int matchMinutes) {
        int end = kickOff.toSecondOfDay() + matchMinutes * 60;
        return kickOff.toSecondOfDay() >= startTime.toSecondOfDay()
                && end <= endTime.toSecondOfDay();
    }
}
