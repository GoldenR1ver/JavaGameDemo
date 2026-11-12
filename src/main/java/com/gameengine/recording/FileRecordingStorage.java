package com.gameengine.recording;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件录制存储实现：负责录像文件的读写操作
 * 实现RecordingStorage接口，处理JSONL格式的录像文件
 */
public class FileRecordingStorage implements RecordingStorage {
    private BufferedWriter writer;

    /**
     * 打开文件写入器，创建必要的目录结构
     */
    @Override
    public void openWriter(String path) throws IOException {
        Path p = Paths.get(path);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        writer = Files.newBufferedWriter(p, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 写入一行数据到录像文件
     */
    @Override
    public void writeLine(String line) throws IOException {
        if (writer == null) throw new IllegalStateException("writer not opened");
        writer.write(line);
        writer.newLine();
    }

    /**
     * 关闭文件写入器，确保数据刷新到磁盘
     */

    @Override
    public void closeWriter() {
        if (writer != null) {
            try { writer.flush(); } catch (Exception ignored) {}
            try { writer.close(); } catch (Exception ignored) {}
            writer = null;
        }
    }

    /**
     * 读取录像文件的所有行
     */
    @Override
    public Iterable<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path), java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 列出recordings目录下的所有录像文件
     * 按最后修改时间倒序排列
     */
    @Override
    public List<File> listRecordings() {
        File dir = new File("recordings");
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".jsonl"));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        return new ArrayList<>(Arrays.asList(files));
    }
}


