package io.github.biliblihuorong.responsiveimages;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ResourcesTest {

    @Test
    void everyExtensionYamlParsesAndNamesItsKind() throws IOException {
        List<Path> files;
        try (var stream = Files.list(Path.of("src/main/resources/extensions"))) {
            files = stream.filter(p -> p.toString().endsWith(".yaml")).toList();
        }
        assertThat(files).isNotEmpty();
        List<Object> documents = new ArrayList<>();
        for (var file : files) {
            new Yaml().loadAll(Files.readString(file)).forEach(documents::add);
        }
        assertThat(documents).allSatisfy(doc -> assertThat(((Map<?, ?>) doc).containsKey("apiVersion") && ((Map<?, ?>) doc).containsKey("kind")).isTrue());
    }
}
