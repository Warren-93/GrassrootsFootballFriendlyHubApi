package com.gffh.api.dto;

import com.gffh.api.model.MessageGif;

import java.util.List;

/** Wire types for the chat's GIF picker ({@code /api/v1/gifs/...}). */
public final class GifDtos {

    private GifDtos() {}

    /**
     * {@code url} is the 200px-tall GIF shown in the chat; {@code previewUrl}
     * the 100px one for the picker grid. Pass {@code id} as {@code gifId}
     * when sending the message.
     */
    public record GifView(String id, String title, String url, String previewUrl, int width, int height) {

        public static GifView from(MessageGif g) {
            return g == null ? null : new GifView(g.id(), g.title(), g.url(), g.previewUrl(), g.width(), g.height());
        }
    }

    public record GifResults(List<GifView> results) {

        public static GifResults of(List<MessageGif> gifs) {
            return new GifResults(gifs.stream().map(GifView::from).toList());
        }
    }
}
