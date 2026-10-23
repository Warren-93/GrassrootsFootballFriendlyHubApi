package com.gffh.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * The admin-edited word list the chat language filter masks. A single
 * document; when none has been saved the built-in starter list applies.
 */
@Data
@NoArgsConstructor
public class ChatFilterSettings {

    /** There is only ever one settings document, always under this id. */
    public static final String SINGLETON_ID = "chat-filter";

    private String id = SINGLETON_ID;
    private List<String> blockedWords = List.of();
    private String updatedByAdminId;
    private Instant updatedAt;

    public ChatFilterSettings(List<String> blockedWords, String updatedByAdminId, Instant updatedAt) {
        this.blockedWords = blockedWords;
        this.updatedByAdminId = updatedByAdminId;
        this.updatedAt = updatedAt;
    }
}
