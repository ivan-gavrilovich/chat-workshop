package com.leverx.chat.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tools exposed to the LLM. Both methods return mocked data; a real implementation would call
 * the OS (e.g. /proc/stat, Runtime.getRuntime()) and query the error log table in the database.
 */
@Service
public class ServerToolsService {

    private static final long TOTAL_RAM_MB = 8192;

    @Tool(description = "Returns the current CPU load (0–100 %) and RAM usage (used MB, total MB, used %) for the application server.")
    public ResourceLoad getServerResourceLoad() {
        long usedRamMb = 7340; // ~90 % of 8 GB
        double ramPercent = Math.round(usedRamMb * 1000.0 / TOTAL_RAM_MB) / 10.0;
        return new ResourceLoad(91.3, usedRamMb, TOTAL_RAM_MB, ramPercent);
    }

    @Tool(description = "Returns the five most recent application error reports from the database.")
    public List<ErrorReport> getRecentErrorReports() {
        return List.of(
                new ErrorReport("2026-10-29 08:47:12", "ERROR", "ChatService",   "Request processing timeout – no response from Gemini after 90 s"),
                new ErrorReport("2026-10-29 08:45:03", "ERROR", "DataSource",    "HikariPool: connection acquisition timeout after 30 s"),
                new ErrorReport("2026-10-29 08:43:55", "WARN",  "ChatService",   "Slow response: Gemini call took 78 s (threshold 60 s)"),
                new ErrorReport("2026-10-29 08:41:30", "ERROR", "DataSource",    "HikariPool: connection acquisition timeout after 30 s"),
                new ErrorReport("2026-10-29 08:38:17", "WARN",  "ApiExceptionHandler", "Repeated 503 errors – upstream unavailable")
        );
    }

    public record ResourceLoad(double cpuLoadPercent, long usedRamMb, long totalRamMb, double ramUsedPercent) {}

    public record ErrorReport(String timestamp, String level, String component, String message) {}
}
