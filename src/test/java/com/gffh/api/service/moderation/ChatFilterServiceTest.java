package com.gffh.api.service.moderation;

import com.gffh.api.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatFilterServiceTest {

    @Test
    void cleanNormalisesDeduplicatesAndSorts() {
        assertEquals(List.of("ass", "shit"), ChatFilterService.clean(Arrays.asList(" Sh1t", "shit", "A$$", "", null)));
    }

    @Test
    void cleanRejectsPhrasesSingleLettersAndOverlongWords() {
        assertThrows(BusinessRuleException.class, () -> ChatFilterService.clean(List.of("two words")));
        assertThrows(BusinessRuleException.class, () -> ChatFilterService.clean(List.of("x")));
        assertThrows(BusinessRuleException.class, () -> ChatFilterService.clean(List.of("a".repeat(41))));
    }
}
