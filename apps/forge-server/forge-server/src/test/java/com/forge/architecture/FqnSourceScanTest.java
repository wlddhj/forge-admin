package com.forge.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 正文全限定名源码扫描守卫（卫生批次第五批 T1）：
 * 后端规范第 0 章禁正文 FQN，历史三次专项清理（06a9603/e23354c/2f69b65）后仍复发——
 * 人工约定失效，改用源码扫描测试固化。
 *
 * <p>检测范围：全部模块 src/main/java（测试代码豁免）。检测形态：非 import/package/
 * 注释行中，以「小写包段.小写包段.」开头且以大写类型名结尾的全限定引用。
 * 豁免：行尾含 {@code // FQN-OK} 的规范豁免行（同名冲突场景，须附原因注释）。
 *
 * <p>已知非目标（正则不做语法分析，宁可漏报不误报）：单段包 FQN（如 {@code java.util.List}
 * 形态中只匹配两段以上以降低误报）、字符串字面量内文本、模型属性链（a.b.c 形态的运行时
 * 取值链以小写开头不匹配大写结尾要求）。
 */
class FqnSourceScanTest {

    /** 两段以上小写包路径 + 大写类型名（如 com.baomidou...Xxx / java.util.concurrent.XXX） */
    private static final Pattern FQN_PATTERN = Pattern.compile(
            "\\b[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){2,}\\.[A-Z]\\w*");

    @Test
    @DisplayName("主代码正文不得使用全限定类名（规范第 0 章；豁免行尾 // FQN-OK）")
    void mainSourcesShouldNotUseFullyQualifiedNames() throws IOException {
        Path root = repoRoot();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> scan(root, p, violations));
        }
        if (!violations.isEmpty()) {
            violations.forEach(System.err::println);
        }
        org.junit.jupiter.api.Assertions.assertTrue(violations.isEmpty(),
                "正文全限定名违规 " + violations.size() + " 处（清单见 stderr；清理或行尾加 // FQN-OK 豁免并注明原因）");
    }

    private void scan(Path root, Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isIgnorable(line) || line.trim().endsWith("// FQN-OK")) {
                    continue;
                }
                Matcher matcher = FQN_PATTERN.matcher(line);
                if (matcher.find()) {
                    violations.add(relative(root, file) + ":" + (i + 1) + "  " + matcher.group()
                            + "  |  " + line.trim());
                }
            }
        } catch (IOException e) {
            violations.add(relative(root, file) + "  读取失败: " + e.getMessage());
        }
    }

    /** import/package/纯注释/javadoc/注解行不检测（注解内 FQN 由 @ 形态排除） */
    private boolean isIgnorable(String line) {
        String t = line.trim();
        return t.isEmpty()
                || t.startsWith("package ")
                || t.startsWith("import ")
                || t.startsWith("//")
                || t.startsWith("*")
                || t.startsWith("/*")
                || t.startsWith("@");
    }

    private Path repoRoot() {
        // 测试运行目录为 forge-server（聚合根）：上级即仓库根；兼容直接在仓库根运行
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(cwd.resolve("apps/forge-server/pom.xml"))) {
            return cwd.resolve("apps/forge-server");
        }
        return cwd;
    }

    private String relative(Path root, Path file) {
        try {
            return root.relativize(file).toString();
        } catch (Exception e) {
            return file.toString();
        }
    }
}
