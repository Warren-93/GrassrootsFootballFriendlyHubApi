package com.gffh.api.config;

import com.gffh.api.model.enums.AbilityLevel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Rewrites teams still stored with the pre-division levels (Development,
 * Intermediate, Competitive) to their division equivalents, so the Discover
 * division filter, which queries the stored value, finds them. Idempotent:
 * once no legacy values remain each startup is three no-op updates.
 * Reads never depend on it - {@link AbilityLevel#fromValue} maps legacy values too.
 */
@Component
public class LegacyDivisionMigration {

    private static final Logger log = LoggerFactory.getLogger(LegacyDivisionMigration.class);

    private final MongoTemplate mongoTemplate;

    public LegacyDivisionMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrate() {
        AbilityLevel.LEGACY.forEach((legacy, division) -> {
            long changed = mongoTemplate.updateMulti(
                    new Query(Criteria.where("abilityLevel").is(legacy)),
                    new Update().set("abilityLevel", division.name()),
                    "teams").getModifiedCount();
            if (changed > 0) log.info("Moved {} teams from {} to {}", changed, legacy, division);
        });
    }
}
