package com.gffh.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Automatic expiry - a friendly request nobody agreed before its date is
 * cancelled and its slots freed, so it stops blocking a new request between
 * the same teams. Hourly, like {@link FixtureCompletionJob}.
 */
@Component
public class RequestExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RequestExpiryJob.class);

    private final FriendlyRequestService friendlyRequestService;

    public RequestExpiryJob(FriendlyRequestService friendlyRequestService) {
        this.friendlyRequestService = friendlyRequestService;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void run() {
        int expired = friendlyRequestService.expirePastDue(LocalDate.now(FriendlyRequestService.FIXTURE_ZONE));
        if (expired > 0) {
            log.info("Expired {} friendly request(s) whose date passed", expired);
        }
    }
}
