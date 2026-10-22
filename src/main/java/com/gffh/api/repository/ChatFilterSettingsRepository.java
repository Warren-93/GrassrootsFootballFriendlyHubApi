package com.gffh.api.repository;

import com.gffh.api.model.ChatFilterSettings;

import java.util.Optional;

public interface ChatFilterSettingsRepository {

    /** Empty until an admin first saves a list. */
    Optional<ChatFilterSettings> find();

    ChatFilterSettings save(ChatFilterSettings settings);

    /** Removes the saved list, so the built-in starter list applies again. */
    void delete();
}
