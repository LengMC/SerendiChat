package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.config.ChatConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天日志记录器：异步写文件，避免主线程阻塞。
 * 写操作由单个守护线程消费，单一 BufferedWriter 复用，避免反复开关文件。
 */
public class ChatLogger implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatConfig config;
    private final Path logFile;
    private final LinkedBlockingQueue<String> queue;
    private final Thread writerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ChatLogger(ChatConfig config) {
        this.config = config;
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.logFile = dir.resolve("serendichat_chat.log");
        if (config.chatLogEnabled) {
            // 仅当日志开启时才启动后台线程，避免闲置线程占用资源
            this.queue = new LinkedBlockingQueue<>(4096);
            running.set(true);
            this.writerThread = new Thread(this::drain, "SerendiChat-LogWriter");
            this.writerThread.setDaemon(true);
            this.writerThread.start();
        } else {
            this.queue = null;
            this.writerThread = null;
        }
    }

    /** 记录一条聊天消息（不包含格式化的颜色代码，纯文本）。 */
    public void log(String playerName, String message) {
        if (!config.chatLogEnabled || queue == null) return;
        String entry = "[" + TS.format(LocalDateTime.now()) + "] <" + playerName + "> " + message;
        if (!queue.offer(entry)) {
            // 队列满，丢弃并提示（避免内存泄漏）
            LOGGER.warn("Chat log queue full, dropping message from {}", playerName);
        }
    }

    private void drain() {
        BufferedWriter writer = null;
        try {
            Files.createDirectories(logFile.getParent());
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            while (running.get()) {
                try {
                    String entry = queue.poll(1, TimeUnit.SECONDS);
                    if (entry != null) {
                        writer.write(entry);
                        writer.newLine();
                        writer.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    LOGGER.error("Failed to write chat log entry", e);
                }
            }
            // 关闭前排空剩余队列
            String remaining;
            while ((remaining = queue.poll()) != null) {
                writer.write(remaining);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to open chat log file", e);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
