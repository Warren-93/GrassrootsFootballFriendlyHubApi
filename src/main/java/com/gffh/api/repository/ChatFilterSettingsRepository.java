package com.gffh.api.repository;

import com.gffh.api.model.ChatFilterSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** One stored document at most, under {@link ChatFilterSettings#SINGLETON_ID}. */
public interface ChatFilterSettingsRepository extends MongoRepository<ChatFilterSettings, String> {

    /** Empty until an admin first saves a list. */
    default Optional<ChatFilterSettings> find() {
        return findById(ChatFilterSettings.SINGLETON_ID);
    }

    /** Removes the saved list, so the built-in starter list applies again. */
    default void delete() {
        deleteById(ChatFilterSettings.SINGLETON_ID);
    }
}
