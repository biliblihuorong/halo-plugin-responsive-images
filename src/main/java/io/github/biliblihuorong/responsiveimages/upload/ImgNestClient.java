package io.github.biliblihuorong.responsiveimages.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ImgNest /api/v2 的最小客户端：上传和移入回收站。
 * Token 只放在 Authorization 头里，不出现在日志和异常信息中。
 */
public class ImgNestClient {

    static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(2);
    static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;

    public ImgNestClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<UploadedImage> upload(ImgNestPolicyConfig config, Flux<DataBuffer> content, String filename,
                                      MediaType mediaType) {
        var base = config.baseUri();
        if (base == null) {
            return Mono.error(new ImgNestException(0, 0, "ImgNest 地址无效，需要以 http:// 或 https:// 开头"));
        }
        var body = new MultipartBodyBuilder();
        body.asyncPart("file", content, DataBuffer.class)
            .filename(filename)
            .contentType(mediaType);
        if (config.getPolicyId() != null && config.getPolicyId() > 0) {
            body.part("policy_id", String.valueOf(config.getPolicyId()));
        }
        if (config.getAlbumId() != null && config.getAlbumId() > 0) {
            body.part("album_id", String.valueOf(config.getAlbumId()));
        }
        if (config.publicFlag() != null) {
            body.part("is_public", String.valueOf(config.publicFlag()));
        }
        return webClient.post()
            .uri(resolve(base, "/api/v2/upload"))
            .header(HttpHeaders.AUTHORIZATION, bearer(config))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body.build()))
            .exchangeToMono(response -> envelope(response))
            .timeout(UPLOAD_TIMEOUT)
            .map(data -> {
                try {
                    return UploadedImage.parse(data);
                } catch (IllegalArgumentException e) {
                    throw new ImgNestException(200, 0, "ImgNest 的上传响应缺少图片 ID 或链接");
                }
            })
            .onErrorMap(WebClientRequestException.class, ImgNestClient::unreachable);
    }

    /** 把图片移进 ImgNest 回收站。图片已经不存在（404）也算成功。 */
    public Mono<Void> delete(ImgNestPolicyConfig config, long id) {
        var base = config.baseUri();
        if (base == null) {
            return Mono.error(new ImgNestException(0, 0, "ImgNest 地址无效，需要以 http:// 或 https:// 开头"));
        }
        return webClient.delete()
            .uri(resolve(base, "/api/v2/images/" + id))
            .header(HttpHeaders.AUTHORIZATION, bearer(config))
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono(response -> response.statusCode().value() == 404
                ? response.releaseBody()
                : envelope(response).then())
            .timeout(DELETE_TIMEOUT)
            .onErrorMap(WebClientRequestException.class, ImgNestClient::unreachable);
    }

    /**
     * 按 JSON 解析响应，不看 Content-Type：反向代理或 CDN 可能改写成 text/plain 之类。
     * 解析不了时把状态、类型和开头一段内容放进错误里，方便判断是哪一层返回的。
     */
    private static Mono<JsonNode> envelope(ClientResponse response) {
        var status = response.statusCode().value();
        var contentType = response.headers().contentType().map(MediaType::toString).orElse("");
        return response.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .flatMap(bytes -> {
                JsonNode body;
                try {
                    body = MAPPER.readTree(bytes);
                } catch (IOException e) {
                    body = null;
                }
                if (body == null || !body.isObject()) {
                    return Mono.error(new ImgNestException(status, 0, "响应不是 JSON（Content-Type: "
                        + (contentType.isEmpty() ? "无" : contentType) + "）：" + snippet(bytes)));
                }
                var code = body.path("code").asInt(-1);
                if (status >= 200 && status < 300 && code == 0) {
                    return Mono.just(body.path("data"));
                }
                return Mono.error(new ImgNestException(status, Math.max(code, 0), body.path("message").asText("")));
            });
    }

    private static String snippet(byte[] bytes) {
        var text = new String(bytes, 0, Math.min(bytes.length, 160), StandardCharsets.UTF_8)
            .replaceAll("\\s+", " ").strip();
        return text.isEmpty() ? "（空）" : text;
    }

    private static ImgNestException unreachable(WebClientRequestException e) {
        return new ImgNestException(0, 0, "连不上 ImgNest：" + e.getMostSpecificCause().getClass().getSimpleName());
    }

    private static String bearer(ImgNestPolicyConfig config) {
        var token = config.getToken() == null ? "" : config.getToken().trim();
        return "Bearer " + token;
    }

    static URI resolve(URI base, String path) {
        var basePath = base.getPath() == null ? "" : base.getPath();
        return base.resolve(basePath + path);
    }
}
