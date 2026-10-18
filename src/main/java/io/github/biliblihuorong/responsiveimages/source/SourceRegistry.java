package io.github.biliblihuorong.responsiveimages.source;

import io.github.biliblihuorong.responsiveimages.BasicSetting;
import io.github.biliblihuorong.responsiveimages.SourcesSetting;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * 读取插件设置，构造来源，并对外提供查询。
 */
@Slf4j
@Component
public class SourceRegistry {

    /** 一次渲染所需的设置快照。 */
    public record Snapshot(BasicSetting basic, List<VariantSource> sources) {
    }

    private final ReactiveSettingFetcher settingFetcher;
    private final WebClient webClient = WebClient.builder().build();
    private final VariantResolver resolver = new VariantResolver(Clock.systemUTC());

    public SourceRegistry(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    public Mono<Snapshot> snapshot() {
        var basic = settingFetcher.fetch(BasicSetting.GROUP, BasicSetting.class)
            .defaultIfEmpty(new BasicSetting());
        var sources = settingFetcher.fetch(SourcesSetting.GROUP, SourcesSetting.class)
            .defaultIfEmpty(new SourcesSetting());
        return Mono.zip(basic, sources).map(t -> new Snapshot(t.getT1(), build(t.getT2())));
    }

    public Mono<Map<String, List<Variant>>> resolve(Snapshot snapshot, Collection<String> urls) {
        if (snapshot.sources().isEmpty() || urls.isEmpty()) {
            return Mono.just(Map.of());
        }
        var basic = snapshot.basic();
        var ttl = Duration.ofMinutes(Math.max(1, basic.getCacheMinutes()));
        var timeout = Duration.ofMillis(Math.max(100, basic.getTimeoutMillis()));
        return resolver.resolve(urls, snapshot.sources(), ttl, timeout);
    }

    public boolean supports(Snapshot snapshot, String url) {
        return VariantResolver.pick(url, snapshot.sources()).isPresent();
    }

    List<VariantSource> build(SourcesSetting setting) {
        List<VariantSource> result = new ArrayList<>();
        if (setting.getSources() == null) {
            return result;
        }
        for (var config : setting.getSources()) {
            var hosts = HostMatcher.parse(config.getHosts());
            if (hosts.isEmpty()) {
                continue;
            }
            if ("template".equals(config.getType())) {
                result.add(new UrlTemplateSource(hosts, UrlTemplateSource.parseWidths(config.getWidths()),
                    config.getTemplate()));
                continue;
            }
            var endpoint = VariantResolver.toUri(config.getEndpoint());
            if (endpoint == null) {
                log.warn("Skip a lookup source without a valid http(s) endpoint");
                continue;
            }
            result.add(new LookupApiSource(endpoint, hosts, webClient));
        }
        return result;
    }
}
