package com.gffh.api.service.moderation;

import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.ChatFilterSettings;
import com.gffh.api.repository.ChatFilterSettingsRepository;
import com.gffh.api.service.AuditLogService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The chat language filter: every message body goes through {@link #mask}
 * before it is stored, so blocked words are replaced with asterisks for
 * everyone - web, app, notifications and the admin transcript alike.
 *
 * <p>The word list is edited on the Admin site. Until an admin saves one,
 * the built-in starter list ({@code moderation/default-blocked-words.txt})
 * applies. The compiled filter is cached and re-read from the database at
 * most once a minute, so an edit made through another API instance is
 * picked up without a restart; an edit made through this one applies at once.
 */
@Service
public class ChatFilterService {

    static final String DEFAULTS_RESOURCE = "moderation/default-blocked-words.txt";
    static final int MAX_WORDS = 2000;
    static final int MAX_WORD_LENGTH = 40;
    private static final Duration REFRESH_EVERY = Duration.ofMinutes(1);

    private final ChatFilterSettingsRepository settings;
    private final AuditLogService auditLogService;
    private final List<String> defaultWords;

    private volatile LanguageFilter filter;
    private volatile Instant loadedAt = Instant.EPOCH;

    public ChatFilterService(ChatFilterSettingsRepository settings, AuditLogService auditLogService) {
        this.settings = settings;
        this.auditLogService = auditLogService;
        this.defaultWords = loadDefaults();
    }

    /** The text with blocked words masked; unchanged when nothing matched. */
    public LanguageFilter.Result mask(String text) {
        return currentFilter().filter(text);
    }

    /** The list in effect, and whether it is still the built-in one. */
    public View current() {
        return settings.find()
                .map(s -> new View(sorted(s.getBlockedWords()), false, s.getUpdatedByAdminId(), s.getUpdatedAt()))
                .orElseGet(() -> new View(sorted(defaultWords), true, null, null));
    }

    public View update(String adminId, Collection<String> words) {
        List<String> cleaned = clean(words);
        settings.save(new ChatFilterSettings(cleaned, adminId, Instant.now()));
        auditLogService.record(adminId, "CHAT_FILTER_UPDATED", "CHAT_FILTER", "chat-filter",
                cleaned.size() + " words");
        reload();
        return current();
    }

    /** Drops the saved list, so the built-in starter list applies again. */
    public View resetToDefaults(String adminId) {
        settings.delete();
        auditLogService.record(adminId, "CHAT_FILTER_RESET", "CHAT_FILTER", "chat-filter", null);
        reload();
        return current();
    }

    /** What a message would look like with the list in effect - for trying words out on the Admin site. */
    public LanguageFilter.Result preview(String text) {
        return mask(text);
    }

    public record View(List<String> words, boolean usingDefaults, String updatedByAdminId, Instant updatedAt) {}

    private LanguageFilter currentFilter() {
        LanguageFilter f = filter;
        if (f == null || Instant.now().isAfter(loadedAt.plus(REFRESH_EVERY))) {
            f = reload();
        }
        return f;
    }

    private synchronized LanguageFilter reload() {
        List<String> words = settings.find().map(ChatFilterSettings::getBlockedWords).orElse(defaultWords);
        LanguageFilter f = new LanguageFilter(words);
        filter = f;
        loadedAt = Instant.now();
        return f;
    }

    /** Normalised the way they are matched, de-duplicated and sorted; rejects anything that can't match. */
    static List<String> clean(Collection<String> words) {
        if (words == null) {
            throw invalid("Send a list of words.");
        }
        TreeSet<String> out = new TreeSet<>();
        for (String raw : words) {
            if (raw == null || raw.isBlank()) continue;
            String trimmed = raw.trim();
            if (trimmed.length() > MAX_WORD_LENGTH) {
                throw invalid("\"" + trimmed + "\" is too long - words can be up to " + MAX_WORD_LENGTH + " letters.");
            }
            if (trimmed.contains(" ")) {
                throw invalid("\"" + trimmed + "\" has a space in it - add single words only.");
            }
            String n = LanguageFilter.normalise(trimmed);
            if (n.length() < 2) {
                throw invalid("\"" + trimmed + "\" needs at least two letters.");
            }
            out.add(n);
        }
        if (out.size() > MAX_WORDS) {
            throw invalid("The list can hold up to " + MAX_WORDS + " words.");
        }
        return List.copyOf(out);
    }

    private static BusinessRuleException invalid(String message) {
        return new BusinessRuleException("INVALID_CHAT_FILTER", HttpStatus.BAD_REQUEST, message);
    }

    private static List<String> sorted(Collection<String> words) {
        return List.copyOf(new TreeSet<>(words));
    }

    private static List<String> loadDefaults() {
        try (var in = new ClassPathResource(DEFAULTS_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + DEFAULTS_RESOURCE, e);
        }
    }
}
