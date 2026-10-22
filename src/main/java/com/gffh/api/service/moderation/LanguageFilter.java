package com.gffh.api.service.moderation;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks blocked words in chat text with asterisks, one per character, so
 * "you're shit" becomes "you're ****". Immutable; build one per word list.
 *
 * <p>Matching is whole-word, so a blocked word inside an innocent one is
 * left alone ("class", "Scunthorpe", "cocktail"). Each word in the text is
 * normalised before it is compared:
 * <ul>
 *   <li>case and accents are ignored, and common look-alike characters are
 *       read as letters ({@code 0}=o, {@code 1}/{@code !}=i, {@code 3}=e,
 *       {@code 4}/{@code @}=a, {@code 5}/{@code $}=s, {@code 7}=t), so
 *       "$h1t" matches "shit";</li>
 *   <li>a word with a letter repeated three or more times is also compared
 *       with every repeat squeezed out, so "shiiiit" matches "shit" - but
 *       only then, so "as" never matches "ass";</li>
 *   <li>common endings (s, es, ed, er, ers, ing, in, y) are tried off, for
 *       blocked words of four letters or more, so "shits" and "shitting"
 *       match "shit" while "asses"/"assess" never match "ass".</li>
 * </ul>
 * Not caught: words split up with spaces or punctuation ("s h i t"), or
 * deliberate misspellings - add those to the list if they show up.
 */
public final class LanguageFilter {

    /**
     * A run of letters, digits or the look-alike symbols read as letters. A
     * "!" only counts inside a word ("5h!t"), so the one in "shit!" is kept.
     */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}@$]+(?:!+[\\p{L}\\p{M}\\p{N}@$]+)*");
    private static final Pattern REPEAT_3 = Pattern.compile("(.)\\1\\1");
    private static final List<String> SUFFIXES = List.of("ings", "ing", "ers", "er", "es", "ed", "in", "s", "y");
    private static final int MIN_STEM_LENGTH = 4;

    private final Set<String> words;
    private final Set<String> squeezedWords;

    public LanguageFilter(Collection<String> blockedWords) {
        Set<String> normalised = new HashSet<>();
        Set<String> squeezed = new HashSet<>();
        for (String word : blockedWords) {
            String n = normalise(word);
            if (!n.isEmpty()) {
                normalised.add(n);
                squeezed.add(squeeze(n));
            }
        }
        this.words = Set.copyOf(normalised);
        this.squeezedWords = Set.copyOf(squeezed);
    }

    /** The result of filtering one message. */
    public record Result(String text, boolean masked) {}

    public Result filter(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return new Result(text, false);
        }
        Matcher m = WORD.matcher(text);
        StringBuilder out = null;
        int last = 0;
        while (m.find()) {
            if (isBlocked(m.group())) {
                if (out == null) out = new StringBuilder(text.length());
                out.append(text, last, m.start()).append("*".repeat(m.end() - m.start()));
                last = m.end();
            }
        }
        if (out == null) {
            return new Result(text, false);
        }
        out.append(text, last, text.length());
        return new Result(out.toString(), true);
    }

    boolean isBlocked(String token) {
        String n = normalise(token);
        if (n.isEmpty()) return false;
        if (matches(n, words)) return true;
        return REPEAT_3.matcher(n).find() && matches(squeeze(n), squeezedWords);
    }

    private static boolean matches(String word, Set<String> list) {
        if (list.contains(word)) return true;
        for (String suffix : SUFFIXES) {
            if (word.length() > suffix.length() && word.endsWith(suffix)) {
                String stem = word.substring(0, word.length() - suffix.length());
                if (stem.length() >= MIN_STEM_LENGTH && list.contains(stem)) return true;
                // "shitting" -> "shitt" -> "shit": a doubled last consonant before the ending.
                int len = stem.length();
                if (len > MIN_STEM_LENGTH && stem.charAt(len - 1) == stem.charAt(len - 2)
                        && list.contains(stem.substring(0, len - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Lower case, accents stripped, look-alike symbols read as letters, and
     * anything else that isn't a letter dropped. Public so the admin list can
     * be stored in the same form it is matched in.
     */
    public static String normalise(String word) {
        String s = Normalizer.normalize(word, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '0' -> sb.append('o');
                case '1', '!' -> sb.append('i');
                case '3' -> sb.append('e');
                case '4', '@' -> sb.append('a');
                case '5', '$' -> sb.append('s');
                case '7' -> sb.append('t');
                default -> {
                    if (c >= 'a' && c <= 'z') sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** Every run of a repeated letter squeezed to one: "shiiit" -> "shit", "ass" -> "as". */
    private static String squeeze(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 || s.charAt(i) != s.charAt(i - 1)) sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
