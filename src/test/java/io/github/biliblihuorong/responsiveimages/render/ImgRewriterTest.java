package io.github.biliblihuorong.responsiveimages.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.biliblihuorong.responsiveimages.BasicSetting;
import io.github.biliblihuorong.responsiveimages.source.Variant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImgRewriterTest {

    private final BasicSetting setting = new BasicSetting();
    private final List<Variant> variants = List.of(
        new Variant("https://img.test/a_w400_v1.webp", 400, 225),
        new Variant("https://img.test/a_w800_v1.webp", 800, 450));

    @Test
    void prefersLazyAttributeOverPlaceholderSrc() {
        Map<String, String> attrs = Map.of("src", "/loading.gif", "data-lazy-src", "https://img.test/a.jpg");
        var c = ImgRewriter.candidate(attrs::get, setting).orElseThrow();
        assertThat(c.url()).isEqualTo("https://img.test/a.jpg");
        assertThat(c.fromLazyAttribute()).isTrue();
    }

    @Test
    void skipsImagesThatAlreadyHaveSrcset() {
        Map<String, String> attrs = Map.of("src", "https://img.test/a.jpg", "srcset", "x 1x");
        assertThat(ImgRewriter.candidate(attrs::get, setting)).isEmpty();
    }

    @Test
    void addsSrcsetAndSizesButKeepsExistingAttributes() {
        Map<String, String> attrs = new HashMap<>(Map.of("src", "https://img.test/a.jpg", "sizes", "50vw"));
        var c = ImgRewriter.candidate(attrs::get, setting).orElseThrow();
        var result = ImgRewriter.attributes(c, variants, attrs::get, setting);
        assertThat(result).containsExactly(Map.entry("srcset",
            "https://img.test/a_w400_v1.webp 400w, https://img.test/a_w800_v1.webp 800w"));
    }

    @Test
    void writesDimensionsOnlyWhenEnabledAndAbsent() {
        setting.setWriteDimensions(true);
        Map<String, String> attrs = Map.of("src", "https://img.test/a.jpg");
        var c = ImgRewriter.candidate(attrs::get, setting).orElseThrow();
        var result = ImgRewriter.attributes(c, variants, attrs::get, setting);
        assertThat(result).containsEntry("width", "800").containsEntry("height", "450")
            .containsEntry("sizes", setting.getSizes());
    }

    @Test
    void lazySrcsetAttributeForLazyLibraries() {
        setting.setLazySrcsetAttribute("data-srcset");
        Map<String, String> attrs = Map.of("data-src", "https://img.test/a.jpg");
        var c = ImgRewriter.candidate(attrs::get, setting).orElseThrow();
        assertThat(ImgRewriter.attributes(c, variants, attrs::get, setting)).containsKey("data-srcset")
            .doesNotContainKey("srcset");
    }

    @Test
    void replacesSrcsetThatOnlyRepeatsTheOriginal() {
        // theme-earth 等主题对外链图调用 thumbnail.gen，得到的 srcset 每项都是原图。
        Map<String, String> attrs = Map.of("src", "https://img.test/a.jpg",
            "srcset", "https://img.test/a.jpg 400w, https://img.test/a.jpg 800w");
        assertThat(ImgRewriter.candidate(attrs::get, setting)).isPresent();
    }

    @Test
    void keepsUrlsWithCommasAsIs() {
        assertThat(ImgRewriter.srcset(List.of(new Variant("https://o.test/a b.jpg?p=resize,w_400", 400, null))))
            .isEqualTo("https://o.test/a%20b.jpg?p=resize,w_400 400w");
    }
}
