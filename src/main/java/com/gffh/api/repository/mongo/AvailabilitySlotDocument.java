package com.gffh.api.repository.mongo;

import com.gffh.api.model.AvailabilitySlot;
import com.gffh.api.model.enums.AvailabilityStatus;
import com.gffh.api.model.enums.Format;
import com.gffh.api.model.enums.HomeAwayPreference;
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
        d.id = s.getId();
        d.teamId = s.getTeamId();
        d.date = s.getDate();
        d.startTime = s.getStartTime();
        d.endTime = s.getEndTime();
        d.homeAwayPreference = s.getHomeAwayPreference().name();
        d.venueId = s.getVenueId();
        d.format = s.getFormat() == null ? null : s.getFormat().name();
        d.notes = s.getNotes();
        d.status = s.getStatus().name();
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
