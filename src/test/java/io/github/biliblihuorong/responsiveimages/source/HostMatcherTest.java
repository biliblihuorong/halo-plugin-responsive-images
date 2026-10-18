package io.github.biliblihuorong.responsiveimages.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class HostMatcherTest {

    @Test
    void matchesHostIgnoringSchemeAndQuery() {
        var m = HostMatcher.parse("img.example.com");
        assertThat(m.matches(URI.create("https://img.example.com/2026/10/a.jpg?x=1"))).isTrue();
        assertThat(m.matches(URI.create("http://IMG.example.com/a.jpg"))).isTrue();
        assertThat(m.matches(URI.create("https://other.example.com/a.jpg"))).isFalse();
        assertThat(m.matches(URI.create("https://img.example.com.evil.test/a.jpg"))).isFalse();
    }

    @Test
    void matchesPathPrefixAndWildcard() {
        var m = HostMatcher.parse("https://cdn.example.com/blog/\n*.img.test");
        assertThat(m.matches(URI.create("https://cdn.example.com/blog/a.png"))).isTrue();
        assertThat(m.matches(URI.create("https://cdn.example.com/other/a.png"))).isFalse();
        assertThat(m.matches(URI.create("https://a.img.test/a.png"))).isTrue();
        assertThat(m.matches(URI.create("https://img.test/a.png"))).isFalse();
        assertThat(m.matches(URI.create("https://badimg.test/a.png"))).isFalse();
    }

    @Test
    void emptyMatchesNothing() {
        assertThat(HostMatcher.parse(" \n ").isEmpty()).isTrue();
        assertThat(HostMatcher.parse(null).matches(URI.create("https://a.test/x"))).isFalse();
    }
}
