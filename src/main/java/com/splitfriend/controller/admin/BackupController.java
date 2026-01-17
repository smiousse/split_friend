package com.splitfriend.controller.admin;

import com.splitfriend.service.BackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/admin/backup")
public class BackupController {

    private final BackupService backupService;

    @Value("${app.backup.auto-backup.enabled:true}")
    private boolean autoBackupEnabled;

    @Value("${app.backup.auto-backup.cron:0 0 2 * * ?}")
    private String backupCron;

    @Value("${app.backup.max-files:10}")
    private int maxBackups;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public String backupPage(Model model) {
        try {
            List<BackupService.BackupInfo> backups = backupService.listBackups();
            model.addAttribute("backups", backups);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to list backups: " + e.getMessage());
        }

        // Add auto-backup configuration
        model.addAttribute("autoBackupEnabled", autoBackupEnabled);
        model.addAttribute("backupSchedule", formatCronExpression(backupCron));
        model.addAttribute("maxBackups", maxBackups);

        return "admin/backup";
    }

    /**
     * Converts a cron expression to a human-readable format
     */
    private String formatCronExpression(String cron) {
        try {
            String[] parts = cron.split(" ");
            if (parts.length >= 6) {
                int minute = Integer.parseInt(parts[1]);
                int hour = Integer.parseInt(parts[2]);
                String dayOfMonth = parts[3];
                String dayOfWeek = parts[5];

                String timeStr = String.format("%02d:%02d", hour, minute);

                if ("*".equals(dayOfMonth) && "?".equals(dayOfWeek)) {
                    return "Daily at " + timeStr;
                } else if ("?".equals(dayOfMonth) && !"*".equals(dayOfWeek)) {
                    return "Weekly at " + timeStr;
                } else if (!"*".equals(dayOfMonth)) {
                    return "Monthly at " + timeStr;
                }
                return "Custom schedule (" + timeStr + ")";
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return cron;
    }

    @PostMapping("/create")
    public String createBackup(RedirectAttributes redirectAttributes) {
        try {
            backupService.createBackup();
            redirectAttributes.addFlashAttribute("message", "Backup created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create backup: " + e.getMessage());
        }
        return "redirect:/admin/backup";
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadNewBackup() {
        try {
            byte[] backup = backupService.createBackupForDownload();
            String filename = "splitfriend_backup_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".sql";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(backup);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadExistingBackup(@PathVariable String filename) {
        try {
            // Validate filename to prevent path traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            byte[] backup = backupService.downloadBackup(filename);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(backup);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/restore")
    public String restoreFromUpload(@RequestParam("backupFile") MultipartFile backupFile,
                                    RedirectAttributes redirectAttributes) {
        if (backupFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a backup file to restore");
            return "redirect:/admin/backup";
        }

        try {
            backupService.restoreFromBackup(backupFile);
            redirectAttributes.addFlashAttribute("message",
                    "Database restored successfully! You may need to log in again.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to restore backup: " + e.getMessage());
        }
        return "redirect:/admin/backup";
    }

    @PostMapping("/restore/{filename}")
    public String restoreFromServer(@PathVariable String filename,
                                    RedirectAttributes redirectAttributes) {
        // Validate filename to prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            redirectAttributes.addFlashAttribute("error", "Invalid filename");
            return "redirect:/admin/backup";
        }

        try {
            backupService.restoreFromServerBackup(filename);
            redirectAttributes.addFlashAttribute("message",
                    "Database restored successfully from " + filename + "! You may need to log in again.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to restore backup: " + e.getMessage());
        }
        return "redirect:/admin/backup";
    }

    @PostMapping("/delete/{filename}")
    public String deleteBackup(@PathVariable String filename,
                               RedirectAttributes redirectAttributes) {
        // Validate filename to prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            redirectAttributes.addFlashAttribute("error", "Invalid filename");
            return "redirect:/admin/backup";
        }

        try {
            backupService.deleteBackup(filename);
            redirectAttributes.addFlashAttribute("message", "Backup deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete backup: " + e.getMessage());
        }
        return "redirect:/admin/backup";
    }
}
