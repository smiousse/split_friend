package com.splitfriend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;

    @Value("${app.backup.auto-backup.enabled:true}")
    private boolean autoBackupEnabled;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * Scheduled backup task - runs daily at 2:00 AM by default.
     * Can be configured via app.backup.auto-backup.cron property.
     */
    @Scheduled(cron = "${app.backup.auto-backup.cron:0 0 2 * * ?}")
    public void performScheduledBackup() {
        if (!autoBackupEnabled) {
            logger.debug("Automatic backup is disabled, skipping scheduled backup");
            return;
        }

        logger.info("Starting scheduled automatic backup...");

        try {
            Path backupFile = backupService.createBackup();
            logger.info("Scheduled backup completed successfully: {}", backupFile.getFileName());
        } catch (Exception e) {
            logger.error("Scheduled backup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Log backup status on application startup
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void logBackupStatus() {
        if (autoBackupEnabled) {
            logger.info("Automatic daily backups are ENABLED. Backups will run according to configured schedule.");
        } else {
            logger.info("Automatic daily backups are DISABLED.");
        }

        try {
            int backupCount = backupService.listBackups().size();
            logger.info("Current backup count: {}", backupCount);
        } catch (Exception e) {
            logger.warn("Could not list existing backups: {}", e.getMessage());
        }
    }
}
