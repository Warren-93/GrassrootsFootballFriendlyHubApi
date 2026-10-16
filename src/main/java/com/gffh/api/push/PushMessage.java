package com.gffh.api.push;

import java.util.Map;

/**
 * What a push shows (title and body) plus the string data the app reads to
 * open the right screen when it is tapped. Data keys with no value are left
 * out rather than sent empty.
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
