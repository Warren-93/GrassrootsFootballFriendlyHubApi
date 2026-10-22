package com.gffh.api.model;

/**
 * A GIF attached to a chat message. Resolved from GIPHY by the API when the
 * message is sent (see GifService) - clients only ever send the GIF's id, so
 * a message can't point at an arbitrary URL or a GIF above the safe rating.
 */
public record MessageGif(String id, String title, String url, String previewUrl, int width, int height) {
}
