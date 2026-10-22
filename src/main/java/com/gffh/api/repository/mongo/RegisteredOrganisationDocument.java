package com.gffh.api.repository.mongo;

import com.gffh.api.model.RegisteredOrganisation;
import com.gffh.api.model.enums.Organisation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("registered_organisations")
public class RegisteredOrganisationDocument {

    public record LeagueDoc(String id, String name, List<String> divisions) {}

    @Id
    public String id;
    public String name;
    public String shortName;
    public String linkedOrganisation;
    public String contactName;
    public String contactEmail;
    public String website;
    public String notes;
    public List<LeagueDoc> leagues;
    public Instant createdAt;
    public String createdByEmail;
    public Instant updatedAt;

    public static RegisteredOrganisationDocument from(RegisteredOrganisation o) {
        RegisteredOrganisationDocument d = new RegisteredOrganisationDocument();
        d.id = o.id();
        d.name = o.name();
        d.shortName = o.shortName();
        d.linkedOrganisation = o.linkedOrganisation() == null ? null : o.linkedOrganisation().name();
        d.contactName = o.contactName();
        d.contactEmail = o.contactEmail();
        d.website = o.website();
        d.notes = o.notes();
        d.leagues = o.leagues() == null ? new ArrayList<>()
                : o.leagues().stream().map(l -> new LeagueDoc(l.id(), l.name(), l.divisions())).toList();
        d.createdAt = o.createdAt();
        d.createdByEmail = o.createdByEmail();
        d.updatedAt = o.updatedAt();
        return d;
    }

    public RegisteredOrganisation toDomain() {
        List<RegisteredOrganisation.League> mapped = leagues == null ? List.of() : leagues.stream()
                .map(l -> new RegisteredOrganisation.League(l.id(), l.name(),
                        l.divisions() == null ? List.of() : l.divisions()))
                .toList();
        return new RegisteredOrganisation(id, name, shortName,
                linkedOrganisation == null ? null : Organisation.valueOf(linkedOrganisation),
                contactName, contactEmail, website, notes, mapped, createdAt, createdByEmail, updatedAt);
    }
}
