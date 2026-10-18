package io.github.biliblihuorong.responsiveimages.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class VariantResolverTest {

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration TIMEOUT = Duration.ofMillis(200);

    static class FakeSource implements VariantSource {
        final List<List<String>> calls = new ArrayList<>();
        Mono<Map<String, List<Variant>>> response;

        @Override
        public boolean supports(URI uri) {
            return "img.test".equals(uri.getHost());
        }

        @Override
        public Mono<Map<String, List<Variant>>> resolve(List<String> urls) {
            calls.add(List.copyOf(urls));
            return response;
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-18T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final VariantResolver resolver = new VariantResolver(clock);
    private final FakeSource source = new FakeSource();

    @Test
    void batchesMissesAndCachesHitsAndMisses() {
        var a = "https://img.test/a.jpg";
        var b = "https://img.test/b.jpg";
        source.response = Mono.just(Map.of(a, List.of(new Variant("https://img.test/a_w400_v1.webp", 400, 300))));

        var first = resolver.resolve(List.of(a, b, a, "https://other.test/c.jpg"), List.of(source), TTL, TIMEOUT)
            .block();
        var second = resolver.resolve(List.of(a, b), List.of(source), TTL, TIMEOUT).block();

        assertThat(first).containsOnlyKeys(a);
        assertThat(second).containsOnlyKeys(a);
        assertThat(source.calls).containsExactly(List.of(a, b));
    }

    @Test
    void refetchesAfterTtl() {
        var a = "https://img.test/a.jpg";
        source.response = Mono.just(Map.of());
        resolver.resolve(List.of(a), List.of(source), TTL, TIMEOUT).block();
        clock.now = clock.now.plus(TTL).plusSeconds(1);
        resolver.resolve(List.of(a), List.of(source), TTL, TIMEOUT).block();
        assertThat(source.calls).hasSize(2);
    }

    @Test
    void failureReturnsEmptyAndBacksOffBriefly() {
        var a = "https://img.test/a.jpg";
        source.response = Mono.error(new IllegalStateException("down"));
        assertThat(resolver.resolve(List.of(a), List.of(source), TTL, TIMEOUT).block()).isEmpty();
        resolver.resolve(List.of(a), List.of(source), TTL, TIMEOUT).block();
        assertThat(source.calls).hasSize(1);

        clock.now = clock.now.plus(VariantResolver.FAILURE_TTL).plusSeconds(1);
        resolver.resolve(List.of(a), List.of(source), TTL, TIMEOUT).block();
        assertThat(source.calls).hasSize(2);
    }

    @Test
    void timeoutKeepsOriginal() {
        source.response = Mono.never();
        var result = resolver.resolve(List.of("https://img.test/a.jpg"), List.of(source), TTL,
            Duration.ofMillis(50)).block(Duration.ofSeconds(2));
        assertThat(result).isEmpty();
    }

    @Test
    void rejectsNonHttpUrls() {
        assertThat(VariantResolver.toUri("data:image/png;base64,xx")).isNull();
        assertThat(VariantResolver.toUri("javascript:alert(1)")).isNull();
        assertThat(VariantResolver.toUri("//img.test/a.jpg").getScheme()).isEqualTo("https");
    }
}
