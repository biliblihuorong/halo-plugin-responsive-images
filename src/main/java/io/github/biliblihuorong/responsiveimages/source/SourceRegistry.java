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
    private volatile String lastLogged;

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

    /** 设置变化后在日志里打一次实际生效的来源，便于确认配置。 */
    private void logOnChange(SourcesSetting setting, List<VariantSource> built, List<String> problems) {
        var key = String.valueOf(setting);
        if (key.equals(lastLogged)) {
            return;
        }
        lastLogged = key;
        problems.forEach(log::warn);
        if (built.isEmpty()) {
            log.warn("Responsive images: no usable image source is configured, images are left unchanged");
        } else {
            log.info("Responsive images: {} source(s) in use: {}", built.size(),
                String.join(", ", built.stream().map(VariantSource::describe).toList()));
        }
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
        List<String> problems = new ArrayList<>();
        if (setting.getSources() == null) {
            logOnChange(setting, result, problems);
            return result;
        }
        for (var config : setting.getSources()) {
            var hosts = HostMatcher.parse(config.getHosts());
            if (hosts.isEmpty()) {
                problems.add("Responsive images: skip a source without image hosts");
                continue;
            }
            var endpoint = VariantResolver.toUri(config.getEndpoint());
            if ("template".equals(config.getType())) {
                var template = config.getTemplate();
                if (template != null && template.contains("{width}")) {
                    result.add(new UrlTemplateSource(hosts, UrlTemplateSource.parseWidths(config.getWidths()),
                        template));
                    continue;
                }
                // 选了「URL 规则」却没填模板，多半是类型选错了：填了查询接口就按查询接口用。
                if (endpoint == null) {
                    problems.add("Responsive images: skip the URL template source for " + config.getHosts().trim().replaceAll("\\s+", ", ")
                        + ": its template has no {width}");
                    continue;
                }
                problems.add("Responsive images: the source for " + config.getHosts().trim().replaceAll("\\s+", ", ")
                    + " is set to URL template but has no template, using its lookup endpoint instead");
            }
            if (endpoint == null) {
                problems.add("Responsive images: skip the lookup source for " + config.getHosts().trim().replaceAll("\\s+", ", ")
                    + ": it has no valid http(s) endpoint");
                continue;
            }
            result.add(new LookupApiSource(endpoint, hosts, webClient));
        }
        logOnChange(setting, result, problems);
        return result;
    }
}
