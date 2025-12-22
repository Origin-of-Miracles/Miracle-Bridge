package com.originofmiracles.miraclebridge.browser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cef.CefSettings.LogSeverity;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.slf4j.Logger;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;

/**
 * Browser console log interceptor.
 * 
 * Captures JavaScript console messages (console.log, console.error, etc.)
 * and writes them to a JSONL file for easier debugging.
 * 
 * Output file: .minecraft/logs/browser-console.jsonl
 * 
 * JSONL format:
 * {"timestamp":"2024-01-01T12:00:00.000Z","level":"LOG","message":"...","source":"http://...","line":123}
 */
public class BrowserConsoleLogger extends CefDisplayHandlerAdapter {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_FILE_NAME = "browser-console.jsonl";
    private static final DateTimeFormatter ISO_FORMATTER = 
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));
    
    private static BrowserConsoleLogger INSTANCE;
    
    private final File logFile;
    private final BlockingQueue<String> writeQueue;
    private final AtomicBoolean running;
    private Thread writerThread;
    private PrintWriter writer;
    
    /**
     * Get the singleton instance
     */
    public static synchronized BrowserConsoleLogger getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BrowserConsoleLogger();
        }
        return INSTANCE;
    }
    
    private BrowserConsoleLogger() {
        // Create log file in .minecraft/logs/
        File gameDir = Minecraft.getInstance().gameDirectory;
        File logsDir = new File(gameDir, "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        this.logFile = new File(logsDir, LOG_FILE_NAME);
        
        this.writeQueue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(false);
        
        LOGGER.info("BrowserConsoleLogger initialized, output: {}", logFile.getAbsolutePath());
    }
    
    /**
     * Start the async writer thread
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                // Append mode, don't overwrite existing logs
                writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)), true);
                
                // Write session separator
                JsonObject separator = new JsonObject();
                separator.addProperty("timestamp", ISO_FORMATTER.format(Instant.now()));
                separator.addProperty("level", "SESSION");
                separator.addProperty("message", "=== New browser session started ===");
                separator.addProperty("source", "BrowserConsoleLogger");
                separator.addProperty("line", 0);
                writer.println(separator.toString());
                
                writerThread = new Thread(this::writerLoop, "BrowserConsoleLogger-Writer");
                writerThread.setDaemon(true);
                writerThread.start();
                
                LOGGER.info("BrowserConsoleLogger writer thread started");
            } catch (IOException e) {
                LOGGER.error("Failed to open log file for writing", e);
                running.set(false);
            }
        }
    }
    
    /**
     * Stop the async writer thread
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (writerThread != null) {
                writerThread.interrupt();
                try {
                    writerThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Flush remaining entries
            drainQueue();
            
            if (writer != null) {
                writer.close();
                writer = null;
            }
            
            LOGGER.info("BrowserConsoleLogger stopped");
        }
    }
    
    /**
     * Writer thread main loop
     */
    private void writerLoop() {
        while (running.get()) {
            try {
                String entry = writeQueue.take();
                if (writer != null) {
                    writer.println(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Drain remaining queue entries when stopping
     */
    private void drainQueue() {
        String entry;
        while ((entry = writeQueue.poll()) != null) {
            if (writer != null) {
                writer.println(entry);
            }
        }
    }
    
    /**
     * CEF DisplayHandler callback - intercepts console messages
     */
    @Override
    public boolean onConsoleMessage(CefBrowser browser, LogSeverity level, 
                                     String message, String source, int line) {
        // Create JSONL entry
        JsonObject entry = new JsonObject();
        entry.addProperty("timestamp", ISO_FORMATTER.format(Instant.now()));
        entry.addProperty("level", severityToString(level));
        entry.addProperty("message", message);
        entry.addProperty("source", source != null ? source : "");
        entry.addProperty("line", line);
        
        // Add to write queue (non-blocking)
        String jsonLine = entry.toString();
        writeQueue.offer(jsonLine);
        
        // Also log to game console based on level
        String levelName = level != null ? level.name() : "LOG";
        if (levelName.contains("ERROR") || levelName.contains("FATAL")) {
            LOGGER.error("[Browser Console] {} ({}:{})", message, source, line);
        } else if (levelName.contains("WARNING") || levelName.contains("WARN")) {
            LOGGER.warn("[Browser Console] {} ({}:{})", message, source, line);
        } else if (levelName.contains("INFO")) {
            LOGGER.info("[Browser Console] {}", message);
        } else {
            LOGGER.debug("[Browser Console] {}", message);
        }
        
        // Return false to let other handlers also process the message
        return false;
    }
    
    /**
     * Convert CEF LogSeverity to string
     */
    private String severityToString(LogSeverity level) {
        if (level == null) return "LOG";
        String name = level.name();
        // Remove prefix if present (e.g., LOGSEVERITY_ERROR -> ERROR)
        if (name.startsWith("LOGSEVERITY_")) {
            return name.substring("LOGSEVERITY_".length());
        }
        return name;
    }
    
    /**
     * Manually log a message (for Java-side events)
     */
    public void log(String level, String message, String source) {
        JsonObject entry = new JsonObject();
        entry.addProperty("timestamp", ISO_FORMATTER.format(Instant.now()));
        entry.addProperty("level", level);
        entry.addProperty("message", message);
        entry.addProperty("source", source != null ? source : "MiracleBridge");
        entry.addProperty("line", 0);
        
        writeQueue.offer(entry.toString());
    }
    
    /**
     * Get the log file path
     */
    public String getLogFilePath() {
        return logFile.getAbsolutePath();
    }
}
