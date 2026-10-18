package io.github.biliblihuorong.responsiveimages.render;

import io.github.biliblihuorong.responsiveimages.source.SourceRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 改写一段 HTML（文章或页面正文）：收集本页图片，一次批量查询，再写回 srcset。
 * 没有可处理的图片或出错时原样返回，不重新序列化。
 */
@Slf4j
@Component
public class ContentRewriter {

    private final SourceRegistry registry;

    public ContentRewriter(SourceRegistry registry) {
        this.registry = registry;
    }

    public Mono<String> rewrite(String html) {
        if (html == null || !html.contains("<img")) {
            return Mono.justOrEmpty(html);
        }
        return registry.snapshot()
            .flatMap(snapshot -> {
                if (!snapshot.basic().isEnableContent() || snapshot.sources().isEmpty()) {
                    return Mono.just(html);
                }
                var basic = snapshot.basic();
                var document = Jsoup.parseBodyFragment(html);
                document.outputSettings().prettyPrint(false);
                record Pending(Element img, ImgRewriter.Candidate candidate) {
                }
                List<Pending> pending = new ArrayList<>();
                for (var img : document.body().select("img")) {
                    ImgRewriter.candidate(img::attr, basic)
                        .filter(c -> registry.supports(snapshot, c.url()))
                        .ifPresent(c -> pending.add(new Pending(img, c)));
                }
                if (pending.isEmpty()) {
                    return Mono.just(html);
                }
                var urls = new LinkedHashSet<String>();
                pending.forEach(p -> urls.add(p.candidate().url()));
                return registry.resolve(snapshot, urls).map(found -> {
                    var changed = false;
                    for (var p : pending) {
                        var variants = found.get(p.candidate().url());
                        if (variants == null) {
                            continue;
                        }
                        var attrs = ImgRewriter.attributes(p.candidate(), variants, p.img()::attr, basic);
                        attrs.forEach(p.img()::attr);
                        changed |= !attrs.isEmpty();
                    }
                    return changed ? document.body().html() : html;
                });
            })
            .onErrorResume(e -> {
                log.warn("Failed to add responsive images to content: {}", e.toString());
                return Mono.just(html);
            });
    }
}
