package com.gameengine.recording;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 录制存储接口：抽象录像文件的读写操作
 * 支持不同的存储后端（文件系统、网络等）
 */
public interface RecordingStorage {
    // 写入操作
    void openWriter(String path) throws IOException;
    void writeLine(String line) throws IOException;
    void closeWriter();

    // 读取操作
    Iterable<String> readLines(String path) throws IOException;
    List<File> listRecordings();    // 列出可用录像文件
}


