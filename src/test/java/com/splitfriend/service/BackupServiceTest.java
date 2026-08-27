package com.splitfriend.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupServiceTest {

    private static final String SCRIPT = """
            CREATE TABLE widgets (id INT PRIMARY KEY, name VARCHAR(50));
            INSERT INTO widgets VALUES (1, 'gizmo');
            """;

    private JdbcDataSource dataSource;
    private BackupService backupService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        // A distinct in-memory database per test, kept alive by DB_CLOSE_DELAY
        dataSource.setURL("jdbc:h2:mem:backup_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        backupService = new BackupService(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE stale (id INT PRIMARY KEY)");
        }
    }

    private MultipartFile upload(String filename, String content) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(content.isEmpty());
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return file;
    }

    private int countWidgets() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM widgets")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void restoresTheUploadedScript() throws Exception {
        backupService.restoreFromBackup(upload("backup.sql", SCRIPT));

        assertThat(countWidgets()).isEqualTo(1);
    }

    @Test
    void dropsPreExistingObjectsBeforeRestoring() throws Exception {
        backupService.restoreFromBackup(upload("backup.sql", SCRIPT));

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() -> stmt.executeQuery("SELECT * FROM stale"))
                    .hasMessageContaining("STALE");
        }
    }

    /**
     * Regression guard: Jetty's multipart {@code Part.writeTo} MOVES its backing
     * temp file to the destination and adopts that path, so transferTo(File) would
     * make the container's own multipart cleanup fail with NoSuchFileException on
     * the temp file this service deletes. The upload must be read as a stream.
     */
    @Test
    void readsTheUploadAsAStreamAndNeverTakesOwnershipOfThePartFile() throws Exception {
        MultipartFile file = upload("backup.sql", SCRIPT);

        backupService.restoreFromBackup(file);

        verify(file).getInputStream();
        verify(file, never()).transferTo(any(File.class));
        verify(file, never()).transferTo(any(java.nio.file.Path.class));
    }

    @Test
    void rejectsANonSqlFilename() throws Exception {
        MultipartFile file = upload("backup.zip", SCRIPT);

        assertThatThrownBy(() -> backupService.restoreFromBackup(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Must be a .sql file");
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        MultipartFile file = upload("backup.sql", "");

        assertThatThrownBy(() -> backupService.restoreFromBackup(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsAFileThatIsNotAnSqlBackup() throws Exception {
        MultipartFile file = upload("backup.sql", "just some prose, no DDL at all\n");

        assertThatThrownBy(() -> backupService.restoreFromBackup(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid backup file format");
    }

    @Test
    void leavesNoRestoreTempFileBehind() throws Exception {
        long before = tempRestoreFileCount();

        backupService.restoreFromBackup(upload("backup.sql", SCRIPT));

        assertThat(tempRestoreFileCount()).isEqualTo(before);
    }

    private long tempRestoreFileCount() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"));
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(tmp)) {
            return files.filter(p -> p.getFileName().toString().startsWith("restore_")).count();
        }
    }
}
