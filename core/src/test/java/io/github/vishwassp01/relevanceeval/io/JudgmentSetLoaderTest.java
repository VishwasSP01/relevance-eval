package io.github.vishwassp01.relevanceeval.io;

import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgmentSetLoaderTest {

    private JudgmentSetLoader loader;

    @BeforeEach
    void setUp() {
        loader = new JudgmentSetLoader();
    }

    @Test
    void loadsValidJudgmentSetFromYamlFile() {
        Path path = resolveTestResource("valid-judgments.yaml");

        JudgmentSet judgmentSet = loader.load(path);

        assertThat(judgmentSet).isNotNull();
        assertThat(judgmentSet.name()).isEqualTo("product-search-baseline");

        assertThat(judgmentSet.queries())
                .containsExactly("waterproof jacket", "running shoes");

        List<Judgment> jacketJudgments = judgmentSet.judgmentsFor("waterproof jacket");
        assertThat(jacketJudgments).containsExactly(
                new Judgment("waterproof jacket", "SKU-1042", 3),
                new Judgment("waterproof jacket", "SKU-8891", 2)
        );

        List<Judgment> shoeJudgments = judgmentSet.judgmentsFor("running shoes");
        assertThat(shoeJudgments).containsExactly(
                new Judgment("running shoes", "SKU-2201", 3)
        );
    }

    @Test
    void throwsReadableExceptionWhenYamlIsMalformed() {
        Path path = resolveTestResource("malformed-judgments.yaml");

        assertThatThrownBy(() -> loader.load(path))
                .isInstanceOf(JudgmentSetException.class)
                .hasMessageContaining(path.toString())
                .hasMessageContaining("Malformed YAML");
    }

    @Test
    void throwsReadableExceptionWhenFileDoesNotExist() {
        Path missingPath = Path.of("src/test/resources/non-existent-judgments.yaml");

        assertThatThrownBy(() -> loader.load(missingPath))
                .isInstanceOf(JudgmentSetException.class)
                .hasMessageContaining("non-existent-judgments.yaml")
                .hasMessageContaining("File does not exist");
    }

    @Test
    void throwsReadableExceptionWhenGradeIsOutOfRange() {
        Path path = resolveTestResource("invalid-grade-judgments.yaml");

        assertThatThrownBy(() -> loader.load(path))
                .isInstanceOf(JudgmentSetException.class)
                .hasMessageContaining(path.toString())
                .hasMessageContaining("Grade 5")
                .hasMessageContaining("out of range");
    }

    private Path resolveTestResource(String filename) {
        URL resource = getClass().getClassLoader().getResource(filename);
        if (resource != null) {
            try {
                return Path.of(resource.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        Path direct = Path.of("src/test/resources", filename);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("core/src/test/resources", filename);
    }
}
