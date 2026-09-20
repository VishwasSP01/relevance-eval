# relevance-eval
Test your search relevance like you test your code. Measure whether ranking changes actually improved results.

## Testing

### Unit Tests
Runs all fast unit tests across modules. Does not require Docker:
```bash
./gradlew test
```

### Integration Tests
Runs containerized integration tests (e.g., Elasticsearch with Testcontainers). Requires a running Docker environment:
```bash
./gradlew integrationTest
```
