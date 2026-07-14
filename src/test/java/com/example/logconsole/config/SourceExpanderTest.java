package com.example.logconsole.config;

import com.example.logconsole.TestFixtures;
import com.example.logconsole.model.ExpandedSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceExpanderTest {
    @Test void expandsLocationAndDimensionCartesianProduct() {
        AppConfig config = TestFixtures.config("http://127.0.0.1:8080/");
        List<ExpandedSource> sources = new SourceExpander(config).expandCurrent("example");
        assertEquals(8, sources.size());
        assertEquals("test/a/01/01", sources.get(0).getLabel());
        assertTrue(sources.get(7).getUrl().getPath().endsWith("location-b/02/example.app-02.log"));
    }

    @Test void resolvesArchiveDate() {
        AppConfig config = TestFixtures.config("http://127.0.0.1:8080/");
        ExpandedSource source = new SourceExpander(config).expandArchive("example", LocalDate.of(2026, 7, 13)).get(0);
        assertTrue(source.getUrl().getPath().endsWith("example.app-01.2026-07-13.log"));
    }

    @Test void rejectsUnknownPlaceholdersAndDuplicateUrls() {
        AppConfig unknown = TestFixtures.config("http://127.0.0.1:8080/");
        unknown.applications.get("example").currentPathTemplate = "logs/{unknown}.log";
        assertThrows(IllegalArgumentException.class, () -> new SourceExpander(unknown).expandCurrent("example"));

        AppConfig duplicate = TestFixtures.config("http://127.0.0.1:8080/");
        duplicate.applications.get("example").currentPathTemplate = "same.log";
        assertThrows(IllegalArgumentException.class, () -> new SourceExpander(duplicate).expandCurrent("example"));
    }

    @Test void rejectsInsecureRemoteHttp() {
        AppConfig config = TestFixtures.config("http://192.0.2.1/");
        assertThrows(IllegalArgumentException.class, () -> new SourceExpander(config).expandCurrent("example"));
    }
}
