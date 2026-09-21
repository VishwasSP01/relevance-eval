package io.github.vishwassp01.relevanceeval.backend.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class ElasticsearchBackendIT {

    private static final DockerImageName ELASTICSEARCH_IMAGE =
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.0");

    @Container
    private static final ElasticsearchContainer CONTAINER =
            new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
                    .withEnv("xpack.security.enabled", "false");

    private static RestClient restClient;
    private static ElasticsearchClient client;
    private static final String INDEX_NAME = "test-products";

    @BeforeAll
    static void setUpAll() throws IOException {
        CONTAINER.start();

        restClient = RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);

        // Create the index
        client.indices().create(c -> c.index(INDEX_NAME));

        // Index four small test documents
        record Product(String title, String description) {}

        // doc-1: strong match on both title and description for "waterproof jacket"
        client.index(i -> i.index(INDEX_NAME).id("doc-1")
                .document(new Product("Waterproof Rain Jacket", "High quality waterproof breathable jacket for hiking")));

        // doc-2: good match on title
        client.index(i -> i.index(INDEX_NAME).id("doc-2")
                .document(new Product("Waterproof Shell", "Lightweight weather resistant shell")));

        // doc-3: partial match
        client.index(i -> i.index(INDEX_NAME).id("doc-3")
                .document(new Product("Fleece Jacket", "Warm fleece jacket for chilly weather")));

        // doc-4: irrelevant document
        client.index(i -> i.index(INDEX_NAME).id("doc-4")
                .document(new Product("Running Shoes", "Road running shoes with cushioned soles")));

        // Refresh index to make documents immediately searchable
        client.indices().refresh(r -> r.index(INDEX_NAME));
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
        if (CONTAINER != null) {
            CONTAINER.stop();
        }
    }

    @Test
    void executesMultiMatchQueryAndReturnsOrderedResultsWithPositiveRanks() {
        ElasticsearchBackend backend = new ElasticsearchBackend(client, INDEX_NAME);

        SearchContext context = new SearchContext(
                INDEX_NAME,
                10,
                Map.of("fields", List.of("title", "description"))
        );

        List<SearchResult> results = backend.search("waterproof jacket", context);

        assertThat(results).isNotEmpty();

        // doc-1 matches both keywords in title and description, so it ranks highest
        assertThat(results.get(0).docId()).isEqualTo("doc-1");
        assertThat(results.get(0).rank()).isEqualTo(1);

        // Verify ranks start at 1 and increment consecutively
        for (int i = 0; i < results.size(); i++) {
            assertThat(results.get(i).rank()).isEqualTo(i + 1);
        }

        // Verify scores are in descending order
        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).score()).isGreaterThanOrEqualTo(results.get(i + 1).score());
        }

        // Irrelevant document doc-4 (running shoes) should not match or appear
        assertThat(results).extracting(SearchResult::docId).doesNotContain("doc-4");

        // Verify backend name formatting
        assertThat(backend.name()).isEqualTo("elasticsearch[" + INDEX_NAME + "]");
    }
}
