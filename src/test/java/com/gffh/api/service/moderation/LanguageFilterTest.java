package com.gffh.api.service.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguageFilterTest {

    private final LanguageFilter filter = new LanguageFilter(List.of("shit", "ass", "twat", "wank"));

    @Test
    void masksABlockedWordWithOneAsteriskPerCharacter() {
        LanguageFilter.Result r = filter.filter("That was shit, ref.");
        assertEquals("That was ****, ref.", r.text());
        assertTrue(r.masked());
    }

    @Test
    void leavesCleanTextAloneAndSaysSo() {
        LanguageFilter.Result r = filter.filter("See you Saturday at 10!");
        assertEquals("See you Saturday at 10!", r.text());
        assertFalse(r.masked());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHIT", "Shit", "sh1t", "$hit", "5h!t", "shiiiit", "shits", "shitting", "shitty",
            "ASS", "a$$", "assss", "twats", "wanker", "wankers", "wanking"})
    void catchesCaseLookAlikesStretchingAndEndings(String word) {
        assertTrue(filter.isBlocked(word), word);
    }

    @ParameterizedTest
    @ValueSource(strings = {"as", "class", "assess", "asses", "bass", "assist", "Scunthorpe", "shitake", "Saturday",
            "passing", "massive", "grass", "a", "10"})
    void leavesInnocentWordsThatMerelyContainABlockedOneAlone(String word) {
        assertFalse(filter.isBlocked(word), word);
    }

    @Test
    void masksEveryOccurrenceAndKeepsTheRestOfTheMessage() {
        assertEquals("****, that **** pitch is ****.\nSee you at 10?",
                filter.filter("Shit, that sh1t pitch is SHIT.\nSee you at 10?").text());
    }

    @Test
    void keepsPunctuationAroundAMaskedWord() {
        assertEquals("****! (****?)", filter.filter("Shit! (twat?)").text());
    }

    @Test
    void ignoresAccents() {
        assertTrue(new LanguageFilter(List.of("merde")).isBlocked("mérde"));
    }

    @Test
    void anEmptyListMasksNothing() {
        assertFalse(new LanguageFilter(List.of()).filter("shit").masked());
    }

    @Test
    void normaliseIsHowTheAdminListIsStored() {
        assertEquals("shit", LanguageFilter.normalise(" Sh1t "));
        assertEquals("ass", LanguageFilter.normalise("A$$"));
    }
}
