package com.gffh.api.repository.mongo;

import com.gffh.api.model.ChatFilterSettings;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("chat_filter_settings")
public class ChatFilterSettingsDocument {

    /** There is only ever one document. */
    public static final String SINGLETON_ID = "chat-filter";

    @Id
    public String id;
    public List<String> blockedWords;
    public String updatedByAdminId;
    public Instant updatedAt;

    public static ChatFilterSettingsDocument from(ChatFilterSettings s) {
        ChatFilterSettingsDocument d = new ChatFilterSettingsDocument();
        d.id = SINGLETON_ID;
        d.blockedWords = s.blockedWords();
        d.updatedByAdminId = s.updatedByAdminId();
        d.updatedAt = s.updatedAt();
        return d;
    }

    public ChatFilterSettings toDomain() {
        return new ChatFilterSettings(blockedWords == null ? List.of() : blockedWords, updatedByAdminId, updatedAt);
    }
}
