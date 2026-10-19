package io.github.biliblihuorong.responsiveimages.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.biliblihuorong.responsiveimages.SourcesSetting;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.plugin.ReactiveSettingFetcher;

class SourceRegistryTest {

    private final SourceRegistry registry = new SourceRegistry(mock(ReactiveSettingFetcher.class));

    private static SourcesSetting.SourceConfig source(String type, String endpoint, String template) {
        var config = new SourcesSetting.SourceConfig();
        config.setType(type);
        config.setHosts("img.test");
        config.setEndpoint(endpoint);
        config.setTemplate(template);
        return config;
    }

    private List<VariantSource> build(SourcesSetting.SourceConfig config) {
        var setting = new SourcesSetting();
        setting.setSources(List.of(config));
        return registry.build(setting);
    }

    @Test
    void usesTheLookupEndpointWhenATemplateSourceHasNoTemplate() {
        var sources = build(source("template", "https://imgnest.test/api/variants", ""));
        assertThat(sources).singleElement().isInstanceOf(LookupApiSource.class);
    }

    @Test
    void keepsAUsableTemplate() {
        var sources = build(source("template", "https://imgnest.test/api/variants", "{url}{sep}w={width}"));
        assertThat(sources).singleElement().isInstanceOf(UrlTemplateSource.class);
    }

    @Test
    void skipsATemplateSourceWithNeitherTemplateNorEndpoint() {
        assertThat(build(source("template", "", null))).isEmpty();
    }
}
