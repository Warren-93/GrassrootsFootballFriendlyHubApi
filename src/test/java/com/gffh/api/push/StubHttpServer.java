package com.gffh.api.push;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local HTTP server standing in for Google's and Apple's endpoints: records
 * every request and answers each path with queued replies (the last one
 * repeats), so the senders' real HttpClient code runs end to end.
 */
final class StubHttpServer implements AutoCloseable {

    record Recorded(String method, String path, Headers headers, String body) {
        String header(String name) {
            return headers.getFirst(name);
        }
    }

    private record Reply(int status, String body) {}

    private final HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Reply>> replies = new ConcurrentHashMap<>();

    StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getRawPath();
            requests.add(new Recorded(exchange.getRequestMethod(), path, exchange.getRequestHeaders(), body));
            Reply reply = next(path);
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Queues a reply for {@code path}; once the queue is down to one, that reply repeats. */
    StubHttpServer reply(String path, int status, String body) {
        replies.computeIfAbsent(path, p -> new ArrayDeque<>()).addLast(new Reply(status, body));
        return this;
    }

    List<Recorded> requestsTo(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    List<Recorded> requests() {
        return List.copyOf(requests);
    }

    private synchronized Reply next(String path) {
        Deque<Reply> queue = replies.get(path);
        if (queue == null || queue.isEmpty()) return new Reply(404, "{}");
        return queue.size() > 1 ? queue.pollFirst() : queue.peekFirst();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
