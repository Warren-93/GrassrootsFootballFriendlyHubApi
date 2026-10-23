package com.gffh.api.model;

import com.gffh.api.model.enums.AbilityLevel;
import com.gffh.api.model.enums.AgeGroup;
import com.gffh.api.model.enums.Format;
import com.gffh.api.model.enums.Gender;
import com.gffh.api.model.enums.HomeAwayPreference;
import com.gffh.api.model.enums.Organisation;
import com.gffh.api.model.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A squad. The matching engine operates entirely on this type plus the team's
 * published availability.
 *
 * <p>{@code contactPhone} is populated from persistence but must never reach a
 * client except through a CONFIRMED fixture. See {@code TeamSummaryDto} and
 * {@code FixtureDetailDto} for the two projections that enforce this.
 */
@Document("teams")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    private String id;
    private String clubId;
    private String name;
    private String clubName;
    private String badgeUrl;
    private AgeGroup ageGroup;
    private Gender gender;
    private Format format;
    private AbilityLevel abilityLevel;
    /** Null for teams created before organisations were asked for. */
    private Organisation organisation;
    private String league;
    private String postcode;
    private GeoPoint location;
    private int travelRadiusMiles;
    private HomeAwayPreference homeAwayPreference;
    private String managerName;
    private String contactPhone;
    private String description;
    private VerificationStatus verification;
    private String defaultVenueId;
    private Instant firstFixtureConfirmedAt;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private boolean suspended;
    private boolean archived;
    /** Defaults to true so teams stored before this field existed stay visible and shared. */
    private boolean searchVisible = true;
    private boolean shareContactDetails = true;

    public boolean isVerified() { return verification == VerificationStatus.VERIFIED; }

    /**
     * Age group and format lock once match history exists, so that a team
     * cannot retrospectively change what its past fixtures meant (SCR-PR-02).
     */
    public boolean matchingFieldsLocked() { return firstFixtureConfirmedAt != null; }

    /**
     * The only gate on sending and receiving friendly requests: the team is
     * verified and has somewhere to play. Profile completeness is guidance
     * shown in {@link #profileChecklist(boolean)}, not a gate.
     */
    public boolean readyForRequests(boolean hasVenue) {
        return isVerified() && hasVenue;
    }

    /**
     * What a manager still has to do, in the order the app shows it. Items
     * with {@code requiredForRequests} are the {@link #readyForRequests} gate;
     * the rest improve the profile opponents see.
     */
    public List<ChecklistItem> profileChecklist(boolean hasVenue) {
        return List.of(
                new ChecklistItem("VERIFICATION", "Get your team verified", isVerified(), true),
                new ChecklistItem("VENUE", "Add a venue", hasVenue, true),
                new ChecklistItem("MANAGER_NAME", "Add a manager name", notBlank(managerName), false),
                new ChecklistItem("BADGE", "Upload a team badge", notBlank(badgeUrl), false),
                new ChecklistItem("DESCRIPTION", "Write a short team description", notBlank(description), false),
                new ChecklistItem("LEAGUE", "Add your league", notBlank(league), false),
                new ChecklistItem("CONTACT_PHONE", "Add a contact phone number", notBlank(contactPhone), false));
    }

    /** One line of {@link #profileChecklist(boolean)}. */
    public record ChecklistItem(String key, String label, boolean done, boolean requiredForRequests) {}

    /**
     * A guide to how complete the profile looks to opponents (Volume 2,
     * section 11). Weighted by contribution to match quality rather than by
     * field count. No longer gates requests - see {@link #readyForRequests}.
     */
    public int completenessPercent(boolean hasVenue) {
        int score = 0;
        int total = 0;

        total += 20; if (notBlank(name)) score += 20;
        total += 15; if (notBlank(postcode)) score += 15;
        total += 15; if (notBlank(managerName)) score += 15;
        total += 15; if (hasVenue || homeAwayPreference == HomeAwayPreference.AWAY) score += 15;
        total += 10; if (notBlank(badgeUrl)) score += 10;
        total += 10; if (notBlank(description)) score += 10;
        total += 10; if (notBlank(league)) score += 10;
        total += 5;  if (notBlank(contactPhone)) score += 5;

        return total == 0 ? 0 : (score * 100) / total;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
