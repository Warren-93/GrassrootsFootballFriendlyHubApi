package com.gffh.api.support;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes the shape of everything the integration tests stored - each
 * collection's field paths and the BSON types found at them - to
 * {@code target/mongo-shape.txt} after each integration test class (the
 * database is shared for the whole run, so the last write covers it all).
 * Only runs when
 * {@code -Dgffh.mongoShape=true} is set. Comparing the file before and after a
 * persistence change shows whether stored documents still look the same, so
 * existing production data keeps loading.
 */
final class MongoShapeReport {

    private MongoShapeReport() {}

    static void writeIfEnabled() {
        if (!Boolean.getBoolean("gffh.mongoShape") || !AbstractIntegrationTest.MONGO.isRunning()) {
            return;
        }
        StringBuilder out = new StringBuilder();
        try (MongoClient client = MongoClients.create(AbstractIntegrationTest.MONGO.getConnectionString())) {
            MongoDatabase db = client.getDatabase("gffh-test");
            for (String collection : new TreeSet<>(db.listCollectionNames().into(new java.util.ArrayList<>()))) {
                Map<String, Set<String>> shape = new TreeMap<>();
                Set<String> classes = new TreeSet<>();
                long count = 0;
                for (BsonDocument doc : db.getCollection(collection, BsonDocument.class).find()) {
                    count++;
                    if (doc.containsKey("_class")) classes.add(doc.getString("_class").getValue());
                    walk("", doc, shape);
                }
                out.append("== ").append(collection).append(" (").append(count > 0 ? "has documents" : "empty").append(")\n");
                out.append("   _class: ").append(classes).append('\n');
                shape.forEach((path, types) -> out.append("   ").append(path).append(": ").append(types).append('\n'));
            }
            Files.writeString(Path.of("target", "mongo-shape.txt"), out.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void walk(String prefix, BsonDocument doc, Map<String, Set<String>> shape) {
        doc.forEach((key, value) -> {
            if (key.equals("_class")) return;
            record(prefix + key, value, shape);
        });
    }

    private static void record(String path, BsonValue value, Map<String, Set<String>> shape) {
        shape.computeIfAbsent(path, p -> new TreeSet<>()).add(value.getBsonType().name());
        if (value.isDocument()) {
            walk(path + ".", value.asDocument(), shape);
        } else if (value.isArray()) {
            value.asArray().forEach(v -> record(path + "[]", v, shape));
        }
    }
}
