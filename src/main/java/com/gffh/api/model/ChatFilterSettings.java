package com.gffh.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * The admin-edited word list the chat language filter masks. A single
 * document; when none has been saved the built-in starter list applies.
 */
@Document("chat_filter_settings")
@Data
@NoArgsConstructor
public class ChatFilterSettings {

    /** There is only ever one settings document, always under this id. */
    public static final String SINGLETON_ID = "chat-filter";

    @Id
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
