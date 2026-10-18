package io.github.biliblihuorong.responsiveimages.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class UrlTemplateSourceTest {

    private final UrlTemplateSource source = new UrlTemplateSource(HostMatcher.parse("oss.test"),
        UrlTemplateSource.parseWidths("1600, 400,800,800,x"), "{url}{sep}x-oss-process=image/resize,w_{width}");

    @Test
    void buildsSortedDistinctWidths() {
        var result = source.resolve(List.of("https://oss.test/a.jpg")).block();
        assertThat(result.get("https://oss.test/a.jpg")).extracting(Variant::url).containsExactly(
            "https://oss.test/a.jpg?x-oss-process=image/resize,w_400",
            "https://oss.test/a.jpg?x-oss-process=image/resize,w_800",
            "https://oss.test/a.jpg?x-oss-process=image/resize,w_1600");
    }

    @Test
    void usesAmpersandWhenQueryExists() {
        assertThat(source.build("https://oss.test/a.jpg?v=2", 400))
            .isEqualTo("https://oss.test/a.jpg?v=2&x-oss-process=image/resize,w_400");
    }

    @Test
    void unsupportedWithoutWidthPlaceholder() {
        var bad = new UrlTemplateSource(HostMatcher.parse("oss.test"), List.of(400), "{url}");
        assertThat(bad.supports(URI.create("https://oss.test/a.jpg"))).isFalse();
        assertThat(source.supports(URI.create("https://oss.test/a.jpg"))).isTrue();
    }
}
