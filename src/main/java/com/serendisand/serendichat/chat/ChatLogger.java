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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天日志记录器：异步写文件，避免主线程阻塞。
 * 写操作由单个守护线程消费，避免高并发写文件时的竞态。
 */
public class ChatLogger implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatConfig config;
    private final Path logFile;
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(4096);
    private final Thread writerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ChatLogger(ChatConfig config) {
        this.config = config;
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.logFile = dir.resolve("serendichat_chat.log");
        this.writerThread = new Thread(this::drain, "SerendiChat-LogWriter");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    /** 记录一条聊天消息（不包含格式化的颜色代码，纯文本）。 */
    public void log(String playerName, String message) {
        if (!config.chatLogEnabled) return;
        String entry = "[" + TS.format(LocalDateTime.now()) + "] <" + playerName + "> " + message;
        if (!queue.offer(entry)) {
            // 队列满，丢弃并提示（避免内存泄漏）
            LOGGER.warn("Chat log queue full, dropping message from {}", playerName);
        }
    }

    private void drain() {
        while (running.get()) {
            try {
                String entry = queue.take();
                writeLine(entry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Failed to write chat log", e);
            }
        }
    }

    private void writeLine(String entry) throws IOException {
        Files.createDirectories(logFile.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(entry);
            w.newLine();
        }
    }

    @Override
    public void close() {
        running.set(false);
        writerThread.interrupt();
    }
}
