package io.github.biliblihuorong.responsiveimages.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class LookupApiSourceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void parsesImgNestEnvelopeInRequestOrder() throws Exception {
        var body = mapper.readTree("""
            {"code":0,"message":"ok","data":{"items":[
              {"url":"https://img.test/a.jpg","found":true,"variants":[
                {"url":"https://img.test/a_w800_v1.webp","width":800,"height":450,"bytes":1,"mime":"image/webp"},
                {"url":"https://img.test/a_w400_v1.webp","width":400,"height":225,"bytes":1,"mime":"image/webp"}]},
              {"url":"https://img.test/b.jpg","found":false,"variants":[]}
            ]}}""");
        var result = LookupApiSource.parse(List.of("https://img.test/a.jpg", "https://img.test/b.jpg"), body);
        assertThat(result.get("https://img.test/a.jpg")).containsExactly(
            new Variant("https://img.test/a_w400_v1.webp", 400, 225),
            new Variant("https://img.test/a_w800_v1.webp", 800, 450));
        assertThat(result.get("https://img.test/b.jpg")).isEmpty();
    }

    @Test
    void acceptsBareItems() throws Exception {
        var body = mapper.readTree("""
            {"items":[{"variants":[{"url":"https://x.test/a_400.webp","width":400}]}]}""");
        var result = LookupApiSource.parse(List.of("https://x.test/a.jpg"), body);
        assertThat(result.get("https://x.test/a.jpg")).containsExactly(new Variant("https://x.test/a_400.webp", 400,
            null));
    }

    @Test
    void postsInBatchesOfFifty() throws Exception {
        for (int i = 0; i < 2; i++) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"code\":0,\"data\":{\"items\":[]}}"));
        }
        var source = new LookupApiSource(server.url("/api/variants").uri(), HostMatcher.parse("img.test"),
            WebClient.builder().build());
        var urls = IntStream.range(0, 60).mapToObj(i -> "https://img.test/" + i + ".jpg").toList();

        source.resolve(urls).block();

        var first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("POST");
        assertThat(first.getPath()).isEqualTo("/api/variants");
        assertThat(mapper.readTree(first.getBody().readUtf8()).path("urls").size()).isEqualTo(50);
        assertThat(mapper.readTree(server.takeRequest().getBody().readUtf8()).path("urls").size()).isEqualTo(10);
    }
}
