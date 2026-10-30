package com.gffh.api.service;

import com.gffh.api.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link VerificationService#requireSafeEvidence}: what a team may ask an admin to open. */
class VerificationEvidenceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://myclub.co.uk",
            "http://fulltime.thefa.com/team/123",
            "myclub.co.uk",
            "Facebook: Riverside Rovers U12",
    })
    @DisplayName("links and plain notes are accepted")
    void acceptsLinksAndText(String entry) {
        assertThatCode(() -> VerificationService.requireSafeEvidence(entry)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(document.cookie)",
            "JavaScript:alert(1)",
            "java\tscript:alert(1)",
            " \u0001javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
    })
    @DisplayName("anything a browser would run or open locally is refused")
    void refusesExecutableSchemes(String entry) {
        assertThatThrownBy(() -> VerificationService.requireSafeEvidence(entry))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code())
                .isEqualTo("INVALID_EVIDENCE_URL");
    }
}
