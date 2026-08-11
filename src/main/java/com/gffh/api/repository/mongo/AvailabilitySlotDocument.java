package com.gffh.api.repository.mongo;

import com.gffh.api.domain.AvailabilitySlot;
import com.gffh.api.domain.AvailabilityStatus;
import com.gffh.api.domain.Format;
import com.gffh.api.domain.HomeAwayPreference;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalTime;

@Document("availability")
public class AvailabilitySlotDocument {

    @Id
    public String id;
    public String teamId;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String homeAwayPreference;
    public String venueId;
    public String format;
    public String notes;
    public String status;

    public static AvailabilitySlotDocument from(AvailabilitySlot s) {
        AvailabilitySlotDocument d = new AvailabilitySlotDocument();
        d.id = s.id();
        d.teamId = s.teamId();
        d.date = s.date();
        d.startTime = s.startTime();
        d.endTime = s.endTime();
        d.homeAwayPreference = s.homeAwayPreference().name();
        d.venueId = s.venueId();
        d.format = s.format() == null ? null : s.format().name();
        d.notes = s.notes();
        d.status = s.status().name();
        return d;
    }

    public AvailabilitySlot toDomain() {
        return new AvailabilitySlot(
                id, teamId, date, startTime, endTime,
                HomeAwayPreference.valueOf(homeAwayPreference),
                venueId,
                format == null ? null : Format.valueOf(format),
                notes,
                AvailabilityStatus.valueOf(status));
    }
}
