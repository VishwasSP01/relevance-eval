package io.github.vishwassp01.relevanceeval.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and parses relevance judgment sets from YAML files into {@link JudgmentSet} domain objects.
 * <p>
 * Flattens the hierarchical YAML structure into individual {@link Judgment} entries, performing validation
 * on required fields, document identifiers, and grade bounds.
 */
public class JudgmentSetLoader {

    private final ObjectMapper mapper;

    public JudgmentSetLoader() {
        this.mapper = YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Loads a judgment set from the specified YAML file path.
     *
     * @param path the path to the YAML file
     * @return the parsed {@link JudgmentSet}
     * @throws JudgmentSetException if the file is missing, malformed, or contains invalid grades
     */
    public JudgmentSet load(Path path) {
        if (path == null) {
            throw new JudgmentSetException(null, "Path must not be null");
        }
        if (!Files.exists(path)) {
            throw new JudgmentSetException(path, "File does not exist");
        }
        if (Files.isDirectory(path)) {
            throw new JudgmentSetException(path, "Path points to a directory, not a file");
        }

        JudgmentSetDto dto;
        try {
            dto = mapper.readValue(path.toFile(), JudgmentSetDto.class);
        } catch (JsonProcessingException e) {
            throw new JudgmentSetException(path, "Malformed YAML syntax or structure: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new JudgmentSetException(path, "Unable to read file: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new JudgmentSetException(path, "Malformed YAML content: " + e.getMessage(), e);
        }

        if (dto == null) {
            throw new JudgmentSetException(path, "File is empty or contains no judgment set data");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new JudgmentSetException(path, "Missing or blank 'name' attribute");
        }
        if (dto.queries() == null) {
            throw new JudgmentSetException(path, "Missing 'queries' section");
        }

        List<Judgment> flatJudgments = new ArrayList<>();
        for (QueryDto queryDto : dto.queries()) {
            if (queryDto == null) {
                throw new JudgmentSetException(path, "Encountered empty or null query entry");
            }
            if (queryDto.query() == null || queryDto.query().isBlank()) {
                throw new JudgmentSetException(path, "Encountered query with missing or blank 'query' text");
            }
            if (queryDto.judgments() == null) {
                throw new JudgmentSetException(path, "Query '" + queryDto.query() + "' is missing 'judgments' list");
            }

            for (JudgmentDto judgmentDto : queryDto.judgments()) {
                if (judgmentDto == null) {
                    throw new JudgmentSetException(path, "Query '" + queryDto.query() + "' contains a null judgment entry");
                }
                if (judgmentDto.id() == null || judgmentDto.id().isBlank()) {
                    throw new JudgmentSetException(path, "Query '" + queryDto.query() + "' contains a judgment with missing or blank 'id'");
                }
                if (judgmentDto.grade() == null) {
                    throw new JudgmentSetException(path, "Document '" + judgmentDto.id() + "' under query '" + queryDto.query() + "' is missing a 'grade'");
                }
                if (judgmentDto.grade() < 0 || judgmentDto.grade() > 3) {
                    throw new JudgmentSetException(path, "Grade " + judgmentDto.grade() + " for query '" + queryDto.query()
                            + "' and document '" + judgmentDto.id() + "' is out of range (must be between 0 and 3)");
                }

                try {
                    flatJudgments.add(new Judgment(queryDto.query(), judgmentDto.id(), judgmentDto.grade()));
                } catch (IllegalArgumentException e) {
                    throw new JudgmentSetException(path, e.getMessage(), e);
                }
            }
        }

        try {
            return new JudgmentSet(dto.name(), flatJudgments);
        } catch (IllegalArgumentException e) {
            throw new JudgmentSetException(path, e.getMessage(), e);
        }
    }

    // Internal DTOs for YAML deserialization to decouple domain model from Jackson
    record JudgmentSetDto(
            @JsonProperty("name") String name,
            @JsonProperty("queries") List<QueryDto> queries
    ) {}

    record QueryDto(
            @JsonProperty("query") String query,
            @JsonProperty("judgments") List<JudgmentDto> judgments
    ) {}

    record JudgmentDto(
            @JsonProperty("id") String id,
            @JsonProperty("grade") Integer grade
    ) {}
}
