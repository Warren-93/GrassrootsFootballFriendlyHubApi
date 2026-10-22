package com.gffh.api.service.gif;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.MessageGif;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class GifServiceTest {

    @Test
    void isOffWithoutAKeyAndRefusesToResolve() {
        GifService gifs = new GifService("", "g", RestClient.builder());
        assertFalse(gifs.enabled());
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> gifs.resolveForMessage("abc123"));
        assertEquals("GIFS_DISABLED", e.code());
    }

    @Test
    void rejectsAnIdThatIsNotAGiphyIdBeforeCallingOut() {
        GifService gifs = new GifService("key", "g", RestClient.builder());
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> gifs.resolveForMessage("https://evil.example/x.gif"));
        assertEquals("GIF_NOT_ALLOWED", e.code());
    }

    @Test
    void mapsGiphysFixedHeightRenditions() throws Exception {
        var item = new ObjectMapper().readTree("""
                {"id":"abc123","title":"Goal","rating":"g","images":{
                  "fixed_height":{"url":"https://media.giphy.com/media/abc123/200.gif","width":"356","height":"200"},
                  "fixed_height_small":{"url":"https://media.giphy.com/media/abc123/100.gif","width":"178","height":"100"}}}
                """);
        MessageGif gif = GifService.toGif(item);
        assertEquals(new MessageGif("abc123", "Goal", "https://media.giphy.com/media/abc123/200.gif",
                "https://media.giphy.com/media/abc123/100.gif", 356, 200), gif);
    }

    @Test
    void dropsAGifWithoutAnHttpsUrl() throws Exception {
        var item = new ObjectMapper().readTree("""
                {"id":"abc123","images":{"fixed_height":{"url":"http://media.giphy.com/x.gif"}}}
                """);
        assertNull(GifService.toGif(item));
    }
}
