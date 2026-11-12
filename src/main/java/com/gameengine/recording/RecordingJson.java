package com.gameengine.recording;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 解析工具类：提供简单的 JSON 字段提取和解析功能。
 *
 * 说明：这是"够用即可"的轻量解析器（录像行格式由我们自己生成）。
 * 关键修复：field() 现在能正确跳过字符串内的逗号/花括号，
 * 并返回完整的嵌套对象/数组（旧实现遇到嵌套结构会截断）。
 */
public final class RecordingJson {
    private RecordingJson() {}

    /**
     * 从 JSON 字符串中提取指定字段的原始值（字符串保留引号）。
     */
    public static String field(String json, String key) {
        if (json == null || key == null) return null;

        int keyIdx = findKey(json, key);
        if (keyIdx < 0) return null;

        int colon = json.indexOf(':', keyIdx + key.length());
        if (colon < 0) return null;

        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return null;

        char first = json.charAt(start);

        // 字符串：按转义规则找到闭合引号
        if (first == '"') {
            int i = start + 1;
            boolean escaped = false;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return json.substring(start, i + 1);
                }
                i++;
            }
            return json.substring(start);
        }

        // 对象/数组：按括号配对返回完整结构
        if (first == '{' || first == '[') {
            char open = first;
            char close = open == '{' ? '}' : ']';
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
            return json.substring(start);
        }

        // 数字/布尔/null：读到分隔符
        int i = start;
        while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') {
            i++;
        }
        return json.substring(start, i).trim();
    }

    /** 精确查找 "key"，并确认其后是冒号，避免匹配到字符串值里的同名文本 */
    private static int findKey(String json, String key) {
        String quoted = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int idx = json.indexOf(quoted, from);
            if (idx < 0) return -1;

            boolean escaped = idx > 0 && json.charAt(idx - 1) == '\\';
            if (!escaped) {
                int after = idx + quoted.length();
                while (after < json.length() && Character.isWhitespace(json.charAt(after))) {
                    after++;
                }
                if (after < json.length() && json.charAt(after) == ':') {
                    return idx;
                }
            }
            from = idx + quoted.length();
        }
    }

    /** 去除字符串两端的引号 */
    public static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 安全解析双精度浮点数 */
    public static double parseDouble(String s) {
        if (s == null) return 0.0;
        try {
            return Double.parseDouble(stripQuotes(s));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** 分割顶层 JSON 数组（忽略嵌套结构） */
    public static String[] splitTopLevel(String arr) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                depth++;
            } else if (ch == '}' || ch == ']') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                out.add(arr.substring(start, i));
                start = i + 1;
            }
        }
        if (start < arr.length()) {
            out.add(arr.substring(start));
        }
        return out.stream().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    /** 从指定位置开始提取 JSON 数组内容 */
    public static String extractArray(String json, int startIdx) {
        int i = startIdx;
        if (i >= json.length() || json.charAt(i) != '[') return "";
        int depth = 1;
        int begin = i + 1;
        i++;
        boolean inString = false;
        boolean escaped = false;
        for (; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(begin, i);
                }
            }
        }
        return "";
    }
}
