package io.github.biliblihuorong.responsiveimages.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

class ImgNestClientTest {

    private MockWebServer server;
    private final ImgNestClient client = new ImgNestClient(WebClient.builder().build());

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private ImgNestPolicyConfig config(String path) {
        var config = new ImgNestPolicyConfig();
        config.setEndpoint(server.url(path).toString());
        config.setToken("1|secret");
        return config;
    }

    private static Flux<DataBuffer> bytes(String text) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    private void respond(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json")
            .setBody(body));
    }

    @Test
    void uploadsTheFileWithTokenAndOptions() throws Exception {
        respond(201, "{\"code\":0,\"message\":\"ok\",\"data\":" + UploadedImageTest.BODY + "}");
        var config = config("/");
        config.setPolicyId(3L);
        config.setAlbumId(7L);
        config.setVisibility("private");

        var image = client.upload(config, bytes("PNGDATA"), "a b.png", MediaType.IMAGE_PNG).block();

        assertThat(image.id()).isEqualTo(4);
        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/v2/upload");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer 1|secret");
        var body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"file\"; filename=\"a b.png\"", "Content-Type: image/png", "PNGDATA");
        assertThat(body).contains("name=\"policy_id\"", "\r\n\r\n3\r\n");
        assertThat(body).contains("name=\"album_id\"", "\r\n\r\n7\r\n");
        assertThat(body).contains("name=\"is_public\"", "\r\n\r\nfalse\r\n");
    }

    @Test
    void leavesOptionalFieldsOutAndKeepsTheBasePath() throws Exception {
        respond(201, "{\"code\":0,\"message\":\"ok\",\"data\":" + UploadedImageTest.BODY + "}");
        client.upload(config("/img/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block();
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/img/api/v2/upload");
        assertThat(request.getBody().readUtf8()).doesNotContain("policy_id", "album_id", "is_public");
    }

    @Test
    void readsAGzippedResponseFromAProxy() throws Exception {
        var gzipped = new okio.Buffer();
        try (var sink = okio.Okio.buffer(new okio.GzipSink(gzipped))) {
            sink.writeUtf8("{\"code\":0,\"message\":\"ok\",\"data\":" + UploadedImageTest.BODY + "}");
        }
        server.enqueue(new MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json")
            .setHeader("Content-Encoding", "gzip").setBody(gzipped));
        var image = client.upload(config("/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block();
        assertThat(image.id()).isEqualTo(4);
    }

    @Test
    void readsJsonWhateverTheContentType() {
        server.enqueue(new MockResponse().setResponseCode(201).setHeader("Content-Type", "text/plain")
            .setBody("{\"code\":0,\"message\":\"ok\",\"data\":" + UploadedImageTest.BODY + "}"));
        var image = client.upload(config("/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block();
        assertThat(image.id()).isEqualTo(4);
    }

    @Test
    void explainsAResponseThatIsNotJson() {
        server.enqueue(new MockResponse().setResponseCode(201).setHeader("Content-Type", "text/html")
            .setBody("<html>challenge</html>"));
        assertThatThrownBy(() -> client.upload(config("/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block())
            .isInstanceOfSatisfying(ImgNestException.class, e ->
                assertThat(e.describe()).contains("HTTP 201", "不是 JSON", "text/html", "<html>challenge"));
    }

    @Test
    void reportsErrorsWithoutTheToken() {
        respond(401, "{\"code\":20001,\"message\":\"unauthenticated\",\"data\":null}");
        assertThatThrownBy(() -> client.upload(config("/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block())
            .isInstanceOfSatisfying(ImgNestException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(401);
                assertThat(e.permanent()).isTrue();
                assertThat(e.describe()).contains("HTTP 401", "20001", "Token").doesNotContain("secret");
            });
    }

    @Test
    void explainsADisabledApi() {
        respond(403, "{\"code\":20003,\"message\":\"api_disabled\",\"data\":null}");
        assertThatThrownBy(() -> client.upload(config("/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block())
            .isInstanceOfSatisfying(ImgNestException.class, e -> assertThat(e.describe()).contains("关闭了 API"));
    }

    @Test
    void treatsAnErrorCodeInA2xxAsFailure() {
        respond(200, "{\"code\":30001,\"message\":\"nope\",\"data\":null}");
        assertThatThrownBy(() -> client.upload(config("/"), bytes("x"), "a.png", MediaType.IMAGE_PNG).block())
            .isInstanceOf(ImgNestException.class);
    }

    @Test
    void deletesByIdAndTreatsMissingAsDone() throws Exception {
        respond(200, "{\"code\":0,\"message\":\"ok\",\"data\":null}");
        respond(404, "{\"code\":30004,\"message\":\"not found\",\"data\":null}");
        client.delete(config("/"), 4).block();
        client.delete(config("/"), 5).block();
        var first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("DELETE");
        assertThat(first.getPath()).isEqualTo("/api/v2/images/4");
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer 1|secret");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/v2/images/5");
    }

    @Test
    void deleteFailsOnServerErrors() {
        respond(503, "oops");
        assertThatThrownBy(() -> client.delete(config("/"), 4).block())
            .isInstanceOfSatisfying(ImgNestException.class, e -> assertThat(e.permanent()).isFalse());
    }

    @Test
    void reportsAnUnreachableServer() throws IOException {
        var config = config("/");
        server.shutdown();
        assertThatThrownBy(() -> client.delete(config, 4).block())
            .isInstanceOfSatisfying(ImgNestException.class, e -> {
                assertThat(e.getStatus()).isZero();
                assertThat(e.permanent()).isFalse();
            });
    }
}
