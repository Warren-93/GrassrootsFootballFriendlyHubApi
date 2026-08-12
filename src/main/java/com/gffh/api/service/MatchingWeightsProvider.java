package com.gffh.api.service;

import com.gffh.api.domain.MatchingWeightsVersion;
import com.gffh.api.matching.MatchingWeights;
import com.gffh.api.repository.MatchingWeightsVersionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the live matching configuration.
 *
 * <p>Backed by a published configuration document so that ADM-08 can retune the
 * weights without a deployment, and without a mobile release - the whole reason
 * matching lives on the server. Publishing is versioned and reversible; this
 * class only exposes the current value. Loads the persisted active version at
 * startup, so a restart doesn't silently revert a published change back to
 * {@link MatchingWeights#DEFAULT}.
 */
@Component
public class MatchingWeightsProvider {

    private final MatchingWeightsVersionRepository versions;
    private final AtomicReference<MatchingWeights> current =
            new AtomicReference<>(MatchingWeights.DEFAULT);

    public MatchingWeightsProvider(MatchingWeightsVersionRepository versions) {
        this.versions = versions;
    }

    @PostConstruct
    void loadPersisted() {
        versions.findActive().map(MatchingWeightsVersion::weights).ifPresent(current::set);
    }

    public MatchingWeights current() {
        return current.get();
    }

    /** Called by the admin service after a validated, audited publish. */
    public void publish(MatchingWeights weights) {
        current.set(weights);
    }
}
