package io.github.biliblihuorong.responsiveimages.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.biliblihuorong.responsiveimages.BasicSetting;
import io.github.biliblihuorong.responsiveimages.SourcesSetting;
import io.github.biliblihuorong.responsiveimages.source.SourceRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

class ContentRewriterTest {

    private final ReactiveSettingFetcher fetcher = mock(ReactiveSettingFetcher.class);
    private final BasicSetting basic = new BasicSetting();
    private ContentRewriter rewriter;

    @BeforeEach
    void setUp() {
        var source = new SourcesSetting.SourceConfig();
        source.setType("template");
        source.setHosts("oss.test");
        source.setWidths("400,800");
        source.setTemplate("{url}{sep}w={width}");
        var sources = new SourcesSetting();
        sources.setSources(List.of(source));
        when(fetcher.fetch(eq(BasicSetting.GROUP), eq(BasicSetting.class))).thenReturn(Mono.just(basic));
        when(fetcher.fetch(eq(SourcesSetting.GROUP), eq(SourcesSetting.class))).thenReturn(Mono.just(sources));
        rewriter = new ContentRewriter(new SourceRegistry(fetcher));
    }

    @Test
    void rewritesMatchingImagesOnly() {
        var html = "<p>hi</p><img src=\"https://oss.test/a.jpg\" alt=\"a\"><img src=\"https://other.test/b.jpg\">";
        var out = rewriter.rewrite(html).block();
        assertThat(out).contains("srcset=\"https://oss.test/a.jpg?w=400 400w, https://oss.test/a.jpg?w=800 800w\"")
            .contains("sizes=\"" + basic.getSizes() + "\"")
            .contains("<img src=\"https://other.test/b.jpg\">")
            .startsWith("<p>hi</p>");
    }

    @Test
    void returnsOriginalStringWhenNothingMatches() {
        var html = "<p>&nbsp;x</p><img src=\"https://other.test/b.jpg\">";
        assertThat(rewriter.rewrite(html).block()).isSameAs(html);
    }

    @Test
    void respectsContentSwitch() {
        basic.setEnableContent(false);
        var html = "<img src=\"https://oss.test/a.jpg\">";
        assertThat(rewriter.rewrite(html).block()).isSameAs(html);
    }

    @Test
    void handlesNullContent() {
        assertThat(rewriter.rewrite(null).block()).isNull();
    }
}
