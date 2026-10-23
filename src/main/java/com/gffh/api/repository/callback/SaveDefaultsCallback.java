package com.gffh.api.repository.callback;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Fills in the values every save relies on, just before a model is written:
 *
 * <ul>
 *   <li>a missing String id becomes a random UUID, as the API has always used,
 *       rather than a MongoDB ObjectId;</li>
 *   <li>a field marked {@link CreatedDate} is set to now if it is still empty;</li>
 *   <li>a field marked {@link LastModifiedDate} is set to now on every save.</li>
 * </ul>
 *
 * <p>This deliberately doesn't use Spring Data's {@code @EnableMongoAuditing}:
 * auditing overwrites {@code @CreatedDate} on every new document, while
 * services here sometimes set it themselves and derive other values from it
 * (an invite's {@code expiresAt} is {@code createdAt} plus 30 days).
 */
@Component
public class SaveDefaultsCallback implements BeforeConvertCallback<Object> {

    private final MongoMappingContext mappingContext;

    public SaveDefaultsCallback(MongoMappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        MongoPersistentEntity<?> persistentEntity = mappingContext.getPersistentEntity(entity.getClass());
        if (persistentEntity == null) return entity;

        PersistentPropertyAccessor<Object> accessor = persistentEntity.getPropertyAccessor(entity);
        Instant now = Instant.now();

        MongoPersistentProperty id = persistentEntity.getIdProperty();
        if (id != null && String.class.equals(id.getType()) && accessor.getProperty(id) == null) {
            accessor.setProperty(id, UUID.randomUUID().toString());
        }
        for (MongoPersistentProperty property : persistentEntity) {
            if (property.isAnnotationPresent(CreatedDate.class) && accessor.getProperty(property) == null) {
                accessor.setProperty(property, now);
            } else if (property.isAnnotationPresent(LastModifiedDate.class)) {
                accessor.setProperty(property, now);
            }
        }
        return accessor.getBean();
    }
}
