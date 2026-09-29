package com.example.agentic.agents.scan;

import com.example.agentic.core.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The analyst's scan is real static analysis: run it over the committed baseline. */
class JavaCodebaseScannerTest {

    @Test
    void extractsTypesImportsAndSpringRoutesFromTheBaseline() {
        Path baseline = Path.of(System.getProperty("repo.root", "..")).resolve("shortener-service");
        CodebaseScan scan = new JavaCodebaseScanner().scan(new Workspace(baseline, baseline.resolve("target/unused")).view());

        assertThat(scan.parseErrors()).isEmpty();
        assertThat(scan.files()).contains("src/main/java/com/example/shortener/api/LinkController.java");
        assertThat(scan.classes()).extracting(CodebaseScan.TypeInfo::name)
                .contains("com.example.shortener.service.ShortenerService", "com.example.shortener.domain.ShortLink");
        assertThat(scan.classes()).filteredOn(type -> type.name().endsWith(".ShortLink"))
                .extracting(CodebaseScan.TypeInfo::kind).containsExactly("record");
        assertThat(scan.classes()).filteredOn(type -> type.name().endsWith(".LinkRepository"))
                .extracting(CodebaseScan.TypeInfo::kind).containsExactly("interface");
        assertThat(scan.routes()).extracting(route -> route.method() + " " + route.path() + " " + route.handler())
                .containsExactlyInAnyOrder(
                        "POST /api/v1/links LinkController#create",
                        "GET /{code:[A-Za-z0-9_-]{3,32}} LinkController#redirect",
                        "GET /api/v1/links/{code}/stats LinkController#stats");
        assertThat(scan.imports()).anyMatch(item -> item.target().equals("org.springframework.dao.DuplicateKeyException"));
    }
}
