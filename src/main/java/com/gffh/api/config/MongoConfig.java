package com.gffh.api.config;

import com.gffh.api.model.GeoPoint;
import com.gffh.api.model.enums.AbilityLevel;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

/**
 * How model fields that aren't plain values are stored in MongoDB.
 *
 * <ul>
 *   <li>{@link GeoPoint} is stored as a GeoJSON point,
 *       {@code {type: "Point", coordinates: [longitude, latitude]}}, which is
 *       what the 2dsphere indexes in {@link IndexConfig} and the
 *       {@code $nearSphere} query in team matching expect.</li>
 *   <li>{@link AbilityLevel} reads through {@link AbilityLevel#fromValue} so a
 *       division stored under an old name (DEVELOPMENT, INTERMEDIATE,
 *       COMPETITIVE) still loads. It is written as its name, like every enum.</li>
 * </ul>
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new GeoPointWriter(), new GeoPointReader(), new AbilityLevelReader()));
    }

    @WritingConverter
    static class GeoPointWriter implements Converter<GeoPoint, Document> {
        @Override
        public Document convert(GeoPoint point) {
            return new Document("type", "Point")
                    .append("coordinates", List.of(point.longitude(), point.latitude()));
        }
    }

    @ReadingConverter
    static class GeoPointReader implements Converter<Document, GeoPoint> {
        @Override
        public GeoPoint convert(Document source) {
            List<?> coordinates = source.getList("coordinates", Object.class);
            return new GeoPoint(((Number) coordinates.get(0)).doubleValue(), ((Number) coordinates.get(1)).doubleValue());
        }
    }

    @ReadingConverter
    static class AbilityLevelReader implements Converter<String, AbilityLevel> {
        @Override
        public AbilityLevel convert(String source) {
            return AbilityLevel.fromValue(source);
        }
    }
}
