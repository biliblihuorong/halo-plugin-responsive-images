package io.github.biliblihuorong.responsiveimages.source;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 把图片地址分派给匹配的来源，合并成批量查询，并缓存结果。
 * 查询失败或超时时返回空结果（保持原图），并短暂缓存失败，避免每次渲染都等超时。
 */
@Slf4j
public class VariantResolver {

    static final Duration FAILURE_TTL = Duration.ofMinutes(1);

    private record Entry(List<Variant> variants, long expiresAt) {
    }

    private final Cache<String, Entry> cache = Caffeine.newBuilder().maximumSize(20_000).build();
    private final Clock clock;
    private volatile String signature = "";

    public VariantResolver(Clock clock) {
        this.clock = clock;
    }

    /**
     * 查询一组地址。结果只包含有衍生版本的地址。
     */
    public Mono<Map<String, List<Variant>>> resolve(Collection<String> urls, List<VariantSource> sources,
        Duration ttl, Duration timeout) {
        resetIfChanged(sources);
        var now = clock.millis();
        Map<String, List<Variant>> found = new HashMap<>();
        Map<VariantSource, List<String>> misses = new LinkedHashMap<>();
        for (var url : new LinkedHashSet<>(urls)) {
            var source = pick(url, sources);
            if (source.isEmpty()) {
                continue;
            }
            var entry = cache.getIfPresent(url);
            if (entry != null && entry.expiresAt() > now) {
                if (!entry.variants().isEmpty()) {
                    found.put(url, entry.variants());
                }
                continue;
            }
            misses.computeIfAbsent(source.get(), k -> new ArrayList<>()).add(url);
        }
        if (misses.isEmpty()) {
            return Mono.just(found);
        }
        return Flux.fromIterable(misses.entrySet())
            .flatMap(e -> query(e.getKey(), e.getValue(), ttl, timeout))
            .doOnNext(found::putAll)
            .then(Mono.fromSupplier(() -> found));
    }

    private Mono<Map<String, List<Variant>>> query(VariantSource source, List<String> urls, Duration ttl,
        Duration timeout) {
        var started = clock.millis();
        return source.resolve(urls)
            .timeout(timeout)
            .map(result -> {
                var expiresAt = clock.millis() + ttl.toMillis();
                Map<String, List<Variant>> hits = new HashMap<>();
                for (var url : urls) {
                    var variants = result.getOrDefault(url, List.of());
                    cache.put(url, new Entry(variants, expiresAt));
                    if (!variants.isEmpty()) {
                        hits.put(url, variants);
                    }
                }
                log.info("Variant lookup via {}: {} of {} image(s) have variants ({} ms)", source.describe(),
                    hits.size(), urls.size(), clock.millis() - started);
                if (log.isDebugEnabled()) {
                    urls.stream().filter(url -> !hits.containsKey(url))
                        .forEach(url -> log.debug("No variants for {}", url));
                }
                return hits;
            })
            .onErrorResume(e -> {
                var hint = e instanceof java.util.concurrent.TimeoutException
                    ? " (lookup timeout is " + timeout.toMillis() + " ms, raise it in 基本设置 if the server is slow)" : "";
                log.warn("Variant lookup via {} failed for {} image(s) after {} ms: {}{}", source.describe(),
                    urls.size(), clock.millis() - started, e.toString(), hint);
                var expiresAt = clock.millis() + FAILURE_TTL.toMillis();
                urls.forEach(url -> cache.put(url, new Entry(List.of(), expiresAt)));
                return Mono.just(Map.of());
            });
    }

    static Optional<VariantSource> pick(String url, List<VariantSource> sources) {
        var uri = toUri(url);
        if (uri == null) {
            return Optional.empty();
        }
        return sources.stream().filter(s -> s.supports(uri)).findFirst();
    }

    static URI toUri(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        var s = url.trim();
        if (s.startsWith("//")) {
            s = "https:" + s;
        }
        try {
            var uri = URI.create(s);
            var scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void resetIfChanged(List<VariantSource> sources) {
        var current = String.join("|", sources.stream().map(VariantSource::describe).toList());
        if (!current.equals(signature)) {
            cache.invalidateAll();
            signature = current;
            log.info("Responsive image sources changed, cache cleared: {}", current.isEmpty() ? "(none)" : current);
        }
    }
}
