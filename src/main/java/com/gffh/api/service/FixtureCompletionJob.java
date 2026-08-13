package com.gffh.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Automatic fixture completion - a CONFIRMED fixture needs no explicit
 * action from either team once its date has passed. Runs hourly rather than
 * daily so a fixture doesn't sit visibly CONFIRMED for up to 24 hours after
 * the day is over.
 */
@Component
public class FixtureCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(FixtureCompletionJob.class);

    private final FriendlyRequestService friendlyRequestService;

    public FixtureCompletionJob(FriendlyRequestService friendlyRequestService) {
        this.friendlyRequestService = friendlyRequestService;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void run() {
        int completed = friendlyRequestService.completePastDue(LocalDate.now());
        if (completed > 0) {
            log.info("Auto-completed {} past-due fixture(s)", completed);
        }
    }
}
