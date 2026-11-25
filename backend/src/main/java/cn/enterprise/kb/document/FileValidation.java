package cn.enterprise.kb.document;

import cn.enterprise.kb.common.ApiProblem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;

final class FileValidation {
    record Validated(String name, String extension) {}

    static Validated check(MultipartFile file) {
        if (file.isEmpty()) throw ApiProblem.bad("请上传非空文件。");
        String name = file.getOriginalFilename();
        if (name == null) throw ApiProblem.bad("缺少文件名。");
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.length() > 240 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiProblem.bad("文件名不合法或超过 240 个字符。");
        }
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        try {
            switch (extension) {
                case "pdf" -> pdf(file);
                case "docx" -> docx(file);
                case "txt" -> txt(file);
                default -> throw ApiProblem.bad("只支持 PDF、DOCX 和 UTF-8 TXT。");
            }
        } catch (IOException ex) {
            throw ApiProblem.bad("文件无法读取，内容可能损坏或与扩展名不符。");
        }
        return new Validated(name, extension);
    }

    private static void pdf(MultipartFile file) throws IOException {
        try (var in = file.getInputStream()) {
            var header = new String(in.readNBytes(8), StandardCharsets.US_ASCII);
            if (!header.startsWith("%PDF-")) throw ApiProblem.bad("文件内容不是有效的 PDF。");
        }
    }

    private static void docx(MultipartFile file) throws IOException {
        boolean body = false, manifest = false;
        long expandedSize = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(file.getInputStream())) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 4096 || entry.getName().equalsIgnoreCase("word/vbaProject.bin")) {
                    throw ApiProblem.bad("DOCX 内容超出解析限制或包含宏。");
                }
                body |= entry.getName().equals("word/document.xml");
                manifest |= entry.getName().equals("[Content_Types].xml");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expandedSize += count;
                    if (expandedSize > 80L * 1024 * 1024) throw ApiProblem.bad("DOCX 解包后体积过大。");
                }
            }
        }
        if (!body || !manifest) throw ApiProblem.bad("文件内容不是有效的 DOCX。");
    }

    private static void txt(MultipartFile file) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), decoder))) {
            var chars = new char[4096];
            int count;
            boolean hasText = false;
            while ((count = reader.read(chars)) != -1) {
                for (int i = 0; i < count; i++) {
                    char c = chars[i];
                    if (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t') throw ApiProblem.bad("TXT 文件包含二进制内容。");
                    if (!Character.isWhitespace(c) && c != 0xFEFF) hasText = true;
                }
            }
            if (!hasText) throw ApiProblem.bad("TXT 文件没有可索引的文字。");
        }
    }

    private FileValidation() {}
}
