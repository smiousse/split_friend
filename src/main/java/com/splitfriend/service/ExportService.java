package com.splitfriend.service;

import com.splitfriend.model.Expense;
import com.splitfriend.model.ExpenseSplit;
import com.splitfriend.model.User;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String exportExpensesToCsv(List<Expense> expenses) {
        StringWriter writer = new StringWriter();

        // CSV Header
        writer.append("Date,Description,Amount,Paid By,Split Type,Participants\n");

        for (Expense expense : expenses) {
            writer.append(escapeCsv(expense.getExpenseDate().format(DATE_FORMATTER)));
            writer.append(",");
            writer.append(escapeCsv(expense.getDescription()));
            writer.append(",");
            writer.append(expense.getAmount().toString());
            writer.append(",");
            writer.append(escapeCsv(expense.getPaidBy().getName()));
            writer.append(",");
            writer.append(expense.getSplitType().name());
            writer.append(",");

            StringBuilder participants = new StringBuilder();
            for (ExpenseSplit split : expense.getSplits()) {
                if (participants.length() > 0) participants.append("; ");
                participants.append(split.getUser().getName())
                        .append(": ")
                        .append(split.getAmount());
            }
            writer.append(escapeCsv(participants.toString()));
            writer.append("\n");
        }

        return writer.toString();
    }

    public String exportUserDataToJson(User user, List<Expense> userExpenses) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"user\": {\n");
        json.append("    \"id\": ").append(user.getId()).append(",\n");
        json.append("    \"email\": \"").append(escapeJson(user.getEmail())).append("\",\n");
        json.append("    \"name\": \"").append(escapeJson(user.getName())).append("\",\n");
        json.append("    \"createdAt\": \"").append(user.getCreatedAt()).append("\"\n");
        json.append("  },\n");
        json.append("  \"expenses\": [\n");

        for (int i = 0; i < userExpenses.size(); i++) {
            Expense expense = userExpenses.get(i);
            json.append("    {\n");
            json.append("      \"id\": ").append(expense.getId()).append(",\n");
            json.append("      \"description\": \"").append(escapeJson(expense.getDescription())).append("\",\n");
            json.append("      \"amount\": ").append(expense.getAmount()).append(",\n");
            json.append("      \"date\": \"").append(expense.getExpenseDate()).append("\",\n");
            json.append("      \"splitType\": \"").append(expense.getSplitType()).append("\"\n");
            json.append("    }");
            if (i < userExpenses.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        return json.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
