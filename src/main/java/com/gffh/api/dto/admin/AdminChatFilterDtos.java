package com.gffh.api.dto.admin;

import com.gffh.api.service.moderation.ChatFilterService;
import com.gffh.api.service.moderation.LanguageFilter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Wire types for the Admin site's Chat filter page. */
public final class AdminChatFilterDtos {

    private AdminChatFilterDtos() {}

    /** {@code usingDefaults} is true until an admin saves a list, while the built-in starter list applies. */
    public record ChatFilterView(List<String> words, boolean usingDefaults, String updatedByAdminId, Instant updatedAt) {

        public static ChatFilterView from(ChatFilterService.View v) {
            return new ChatFilterView(v.words(), v.usingDefaults(), v.updatedByAdminId(), v.updatedAt());
        }
    }

    public record UpdateChatFilterRequest(@NotNull List<String> words) {}

    public record PreviewRequest(@NotNull @Size(max = 2000) String text) {}

    public record PreviewView(String text, boolean masked) {

        public static PreviewView from(LanguageFilter.Result r) {
            return new PreviewView(r.text(), r.masked());
        }
    }
}
