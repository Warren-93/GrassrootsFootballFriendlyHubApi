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
        d.id = o.getId();
        d.name = o.getName();
        d.shortName = o.getShortName();
        d.linkedOrganisation = o.getLinkedOrganisation() == null ? null : o.getLinkedOrganisation().name();
        d.contactName = o.getContactName();
        d.contactEmail = o.getContactEmail();
        d.website = o.getWebsite();
        d.notes = o.getNotes();
        d.leagues = o.getLeagues() == null ? new ArrayList<>()
                : o.getLeagues().stream().map(l -> new LeagueDoc(l.id(), l.name(), l.divisions())).toList();
        d.createdAt = o.getCreatedAt();
        d.createdByEmail = o.getCreatedByEmail();
        d.updatedAt = o.getUpdatedAt();
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
