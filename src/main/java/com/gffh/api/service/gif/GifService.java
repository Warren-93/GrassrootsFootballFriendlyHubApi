package com.gffh.api.service.gif;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.MessageGif;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * GIF search for the chat's GIF picker, through GIPHY. The API key stays on
 * the server ({@code GIPHY_API_KEY}): clients call
 * {@code /api/v1/gifs/...}, never GIPHY directly, so the key isn't shipped in
 * the web bundle or the app.
 *
 * <p>Every search asks GIPHY for the configured content rating
 * ({@code GIPHY_RATING}, default {@code g}, GIPHY's strictest). A GIF sent in
 * a message is looked up again by id and its rating re-checked, so a client
 * can't attach a GIF that a search would never have offered.
 *
 * <p>Results are cached for ten minutes per search, since a GIPHY beta key
 * allows only 100 calls an hour.
 */
@Service
public class GifService {

    private static final Logger log = LoggerFactory.getLogger(GifService.class);
    private static final String BASE_URL = "https://api.giphy.com/v1/gifs";
    private static final Pattern GIF_ID = Pattern.compile("[A-Za-z0-9]{1,64}");
    /** GIPHY's ratings from strictest to loosest. */
    private static final List<String> RATINGS = List.of("y", "g", "pg", "pg-13", "r");
    private static final Duration CACHE_FOR = Duration.ofMinutes(10);
    private static final int MAX_CACHED_SEARCHES = 500;
    public static final int MAX_LIMIT = 30;

    private final String apiKey;
    private final String rating;
    private final RestClient http;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public GifService(@Value("${gffh.gifs.giphy.api-key:}") String apiKey,
                      @Value("${gffh.gifs.giphy.rating:g}") String rating,
                      RestClient.Builder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String r = rating == null ? "g" : rating.trim().toLowerCase(Locale.ROOT);
        this.rating = RATINGS.contains(r) ? r : "g";
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.http = builder.baseUrl(BASE_URL).requestFactory(requestFactory).build();
    }

    /** Whether GIFs are switched on - published to clients by {@code GET /api/v1/config}. */
    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    public List<MessageGif> trending(int limit) {
        int n = clampLimit(limit);
        return cached("trending:" + n, () -> results(get("/trending?api_key={key}&limit={limit}&rating={rating}",
                apiKey, n, rating)));
    }

    public List<MessageGif> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return trending(limit);
        if (q.length() > 50) q = q.substring(0, 50);
        int n = clampLimit(limit);
        String term = q;
        return cached("search:" + n + ":" + term.toLowerCase(Locale.ROOT), () -> results(get(
                "/search?api_key={key}&q={q}&limit={limit}&rating={rating}&lang=en", apiKey, term, n, rating)));
    }

    /**
     * The GIF to attach to a message, looked up by id. Rejected when GIFs are
     * off, the id is unknown, or its rating is above the configured one.
     */
    public MessageGif resolveForMessage(String gifId) {
        requireEnabled();
        if (gifId == null || !GIF_ID.matcher(gifId).matches()) {
            throw gifNotAllowed();
        }
        JsonNode data = get("/{id}?api_key={key}", gifId, apiKey).path("data");
        if (data.isMissingNode() || !data.isObject() || !ratingAllowed(data.path("rating").asText(""))) {
            throw gifNotAllowed();
        }
        MessageGif gif = toGif(data);
        if (gif == null) throw gifNotAllowed();
        return gif;
    }

    private boolean ratingAllowed(String gifRating) {
        int index = RATINGS.indexOf(gifRating.toLowerCase(Locale.ROOT));
        return index >= 0 && index <= RATINGS.indexOf(rating);
    }

    private JsonNode get(String uri, Object... vars) {
        requireEnabled();
        try {
            JsonNode body = http.get().uri(uri, vars).retrieve().body(JsonNode.class);
            if (body == null) throw new RestClientException("empty response");
            return body;
        } catch (RestClientException e) {
            // Never log the URI: it carries the API key.
            log.warn("GIPHY request failed: {}", e.getClass().getSimpleName());
            throw new BusinessRuleException("GIFS_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "GIFs can't be loaded right now. Try again in a minute.");
        }
    }

    private List<MessageGif> results(JsonNode body) {
        List<MessageGif> gifs = new ArrayList<>();
        for (JsonNode item : body.path("data")) {
            if (!ratingAllowed(item.path("rating").asText(""))) continue;
            MessageGif gif = toGif(item);
            if (gif != null) gifs.add(gif);
        }
        return List.copyOf(gifs);
    }

    /** fixed_height (200px tall) to show in the chat, fixed_height_small (100px) for the picker grid. */
    static MessageGif toGif(JsonNode item) {
        JsonNode images = item.path("images");
        JsonNode full = images.path("fixed_height");
        JsonNode small = images.path("fixed_height_small");
        String url = full.path("url").asText("");
        if (url.isEmpty() || !url.startsWith("https://")) return null;
        String previewUrl = small.path("url").asText(url);
        return new MessageGif(item.path("id").asText(), item.path("title").asText(""), url,
                previewUrl.startsWith("https://") ? previewUrl : url,
                full.path("width").asInt(0), full.path("height").asInt(0));
    }

    private List<MessageGif> cached(String key, java.util.function.Supplier<List<MessageGif>> load) {
        Instant now = Instant.now();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) return hit.gifs();
        List<MessageGif> gifs = load.get();
        if (cache.size() >= MAX_CACHED_SEARCHES) {
            cache.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
            if (cache.size() >= MAX_CACHED_SEARCHES) cache.clear();
        }
        cache.put(key, new Cached(gifs, now.plus(CACHE_FOR)));
        return gifs;
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new BusinessRuleException("GIFS_DISABLED", HttpStatus.SERVICE_UNAVAILABLE,
                    "GIFs aren't switched on.");
        }
    }

    private static BusinessRuleException gifNotAllowed() {
        return new BusinessRuleException("GIF_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "That GIF can't be sent.");
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 24 : limit, MAX_LIMIT));
    }

    private record Cached(List<MessageGif> gifs, Instant expiresAt) {}
}
