package com.gffh.api.service;

import com.gffh.api.matching.MatchingWeights;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the live matching configuration.
 *
 * <p>Backed by a published configuration document so that ADM-08 can retune the
 * weights without a deployment, and without a mobile release - the whole reason
 * matching lives on the server. Publishing is versioned and reversible; this
 * class only exposes the current value.
 */
@Component
public class MatchingWeightsProvider {

    private final AtomicReference<MatchingWeights> current =
            new AtomicReference<>(MatchingWeights.DEFAULT);

    public MatchingWeights current() {
        return current.get();
    }

    /** Called by the admin service after a validated, audited publish. */
    public void publish(MatchingWeights weights) {
        current.set(weights);
    }
}
