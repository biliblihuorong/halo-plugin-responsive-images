package io.github.biliblihuorong.responsiveimages.source;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 通过批量查询接口获取衍生版本。协议与 ImgNest 的 POST /api/variants 相同：
 * 请求 {"urls":[...]}，响应 {"data":{"items":[{"url","found","variants":[{"url","width","height"}]}]}}，
 * 也接受没有 data 外壳的 {"items":[...]}。items 与请求顺序一致。
 */
public final class LookupApiSource implements VariantSource {

    /** 单批最多 50 个链接，与 ImgNest 的限制一致。 */
    static final int BATCH = 50;

    private final URI endpoint;
    private final HostMatcher hosts;
    private final WebClient webClient;

    public LookupApiSource(URI endpoint, HostMatcher hosts, WebClient webClient) {
        this.endpoint = endpoint;
        this.hosts = hosts;
        this.webClient = webClient;
    }

    @Override
    public boolean supports(URI uri) {
        return hosts.matches(uri);
    }

    @Override
    public Mono<Map<String, List<Variant>>> resolve(List<String> urls) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < urls.size(); i += BATCH) {
            batches.add(urls.subList(i, Math.min(urls.size(), i + BATCH)));
        }
        return Flux.fromIterable(batches)
            .concatMap(this::lookup)
            .reduce(new HashMap<String, List<Variant>>(), (all, part) -> {
                all.putAll(part);
                return all;
            })
            .map(m -> (Map<String, List<Variant>>) m);
    }

    private Mono<Map<String, List<Variant>>> lookup(List<String> batch) {
        return webClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("urls", batch))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(body -> parse(batch, body));
    }

    static Map<String, List<Variant>> parse(List<String> batch, JsonNode body) {
        var items = body.path("data").path("items");
        if (!items.isArray()) {
            items = body.path("items");
        }
        Map<String, List<Variant>> result = new HashMap<>();
        if (!items.isArray()) {
            return result;
        }
        for (int i = 0; i < batch.size() && i < items.size(); i++) {
            var item = items.get(i);
            List<Variant> variants = new ArrayList<>();
            if (item.path("found").asBoolean(true)) {
                for (var v : item.path("variants")) {
                    var url = v.path("url").asText("");
                    var width = v.path("width").asInt(0);
                    if (url.isEmpty() || width <= 0) {
                        continue;
                    }
                    var height = v.path("height").asInt(0);
                    variants.add(new Variant(url, width, height > 0 ? height : null));
                }
            }
            variants.sort(Comparator.comparingInt(Variant::width));
            result.put(batch.get(i), List.copyOf(variants));
        }
        return result;
    }

    @Override
    public String describe() {
        return "lookup:" + endpoint;
    }
}
