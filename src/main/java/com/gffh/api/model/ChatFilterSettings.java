package com.gffh.api.model;

import java.time.Instant;
import java.util.List;

/**
 * The admin-edited word list the chat language filter masks. A single
 * document; when none has been saved the built-in starter list applies.
 */
public record ChatFilterSettings(List<String> blockedWords, String updatedByAdminId, Instant updatedAt) {
}
