package com.splitfriend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BackupService {

    private final DataSource dataSource;

    @Value("${app.backup.directory:./backups}")
    private String backupDirectory;

    @Value("${app.backup.max-files:10}")
    private int maxBackupFiles;

    public BackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a SQL script backup of the database
     * @return the backup file path
     */
    public Path createBackup() throws Exception {
        // Ensure backup directory exists
        Path backupDir = Paths.get(backupDirectory);
        Files.createDirectories(backupDir);

        // Generate backup filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "splitfriend_backup_" + timestamp + ".sql";
        Path backupFile = backupDir.resolve(filename);

        // Use H2's SCRIPT command to create a SQL backup
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SCRIPT TO '" + backupFile.toAbsolutePath().toString().replace("\\", "/") + "'");
        }

        // Clean up old backups if we exceed the maximum
        cleanupOldBackups();

        return backupFile;
    }

    /**
     * Creates a backup and returns it as a byte array for download
     */
    public byte[] createBackupForDownload() throws Exception {
        Path backupFile = createBackup();
        byte[] content = Files.readAllBytes(backupFile);
        return content;
    }

    /**
     * Restores the database from a SQL script file
     * @param backupFile the uploaded backup file
     */
    public void restoreFromBackup(MultipartFile backupFile) throws Exception {
        if (backupFile.isEmpty()) {
            throw new IllegalArgumentException("Backup file is empty");
        }

        // Validate file extension
        String originalFilename = backupFile.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".sql")) {
            throw new IllegalArgumentException("Invalid backup file. Must be a .sql file");
        }

        // Save uploaded file temporarily
        Path tempFile = Files.createTempFile("restore_", ".sql");
        try {
            backupFile.transferTo(tempFile.toFile());

            // Validate the backup file contains expected content
            validateBackupFile(tempFile);

            // Drop all existing data and restore from backup
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Drop all objects first
                stmt.execute("DROP ALL OBJECTS");

                // Run the restore script
                stmt.execute("RUNSCRIPT FROM '" + tempFile.toAbsolutePath().toString().replace("\\", "/") + "'");
            }
        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Restores from a backup file stored on the server
     * @param filename the backup filename
     */
    public void restoreFromServerBackup(String filename) throws Exception {
        Path backupFile = Paths.get(backupDirectory).resolve(filename);

        if (!Files.exists(backupFile)) {
            throw new IllegalArgumentException("Backup file not found: " + filename);
        }

        // Security check: ensure the file is within the backup directory
        if (!backupFile.toAbsolutePath().normalize().startsWith(Paths.get(backupDirectory).toAbsolutePath().normalize())) {
            throw new SecurityException("Invalid backup file path");
        }

        validateBackupFile(backupFile);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Drop all objects first
            stmt.execute("DROP ALL OBJECTS");

            // Run the restore script
            stmt.execute("RUNSCRIPT FROM '" + backupFile.toAbsolutePath().toString().replace("\\", "/") + "'");
        }
    }

    /**
     * Lists all available backup files
     */
    public List<BackupInfo> listBackups() throws IOException {
        Path backupDir = Paths.get(backupDirectory);

        if (!Files.exists(backupDir)) {
            return new ArrayList<>();
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            return files
                    .filter(f -> f.toString().endsWith(".sql"))
                    .map(this::createBackupInfo)
                    .sorted(Comparator.comparing(BackupInfo::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Deletes a backup file
     */
    public void deleteBackup(String filename) throws IOException {
        Path backupFile = Paths.get(backupDirectory).resolve(filename);

        // Security check
        if (!backupFile.toAbsolutePath().normalize().startsWith(Paths.get(backupDirectory).toAbsolutePath().normalize())) {
            throw new SecurityException("Invalid backup file path");
        }

        if (!Files.exists(backupFile)) {
            throw new IllegalArgumentException("Backup file not found: " + filename);
        }

        Files.delete(backupFile);
    }

    /**
     * Downloads a specific backup file
     */
    public byte[] downloadBackup(String filename) throws IOException {
        Path backupFile = Paths.get(backupDirectory).resolve(filename);

        // Security check
        if (!backupFile.toAbsolutePath().normalize().startsWith(Paths.get(backupDirectory).toAbsolutePath().normalize())) {
            throw new SecurityException("Invalid backup file path");
        }

        if (!Files.exists(backupFile)) {
            throw new IllegalArgumentException("Backup file not found: " + filename);
        }

        return Files.readAllBytes(backupFile);
    }

    private BackupInfo createBackupInfo(Path file) {
        try {
            return new BackupInfo(
                    file.getFileName().toString(),
                    Files.size(file),
                    Files.getLastModifiedTime(file).toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
            );
        } catch (IOException e) {
            return new BackupInfo(file.getFileName().toString(), 0, LocalDateTime.now());
        }
    }

    private void validateBackupFile(Path file) throws IOException {
        // Read first few lines to validate it's a H2 SQL script
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.contains("CREATE") && !firstLine.startsWith("--")) {
                // Check if it might be a valid H2 script
                String content = Files.readString(file);
                if (!content.contains("CREATE TABLE") && !content.contains("INSERT INTO")) {
                    throw new IllegalArgumentException("Invalid backup file format. Does not appear to be a valid SQL backup.");
                }
            }
        }
    }

    private void cleanupOldBackups() throws IOException {
        Path backupDir = Paths.get(backupDirectory);

        if (!Files.exists(backupDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            List<Path> backupFiles = files
                    .filter(f -> f.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing((Path f) -> {
                        try {
                            return Files.getLastModifiedTime(f).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .collect(Collectors.toList());

            // Delete files beyond the maximum
            if (backupFiles.size() > maxBackupFiles) {
                for (int i = maxBackupFiles; i < backupFiles.size(); i++) {
                    Files.deleteIfExists(backupFiles.get(i));
                }
            }
        }
    }

    /**
     * Backup information DTO
     */
    public static class BackupInfo {
        private final String filename;
        private final long size;
        private final LocalDateTime createdAt;

        public BackupInfo(String filename, long size, LocalDateTime createdAt) {
            this.filename = filename;
            this.size = size;
            this.createdAt = createdAt;
        }

        public String getFilename() {
            return filename;
        }

        public long getSize() {
            return size;
        }

        public String getFormattedSize() {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            }
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}
