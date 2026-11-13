import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 插件 License 离线签发工具（零第三方依赖，JDK 21 单文件直跑）。
 *
 * 用法：java IssueLicense.java <payload.json> <private-key.b64> <output.lic>
 *
 * 流程：解析 payload.json → canonical 序列化（key 字典序、紧凑无空白、
 * 零歧义数字输出）→ SHA256withRSA 签名 → 输出 {"payload":"<Base64>","signature":"<Base64>"}。
 * 验签端（forge-spring-boot-starter-plugin 的 LicenseVerifier）对 payload
 * 原始字节验签，两端不依赖同一套 JSON 库。
 *
 * payload.json 必填字段：
 *   licenseKey  唯一编号，如 LIC-20261113-0001
 *   pluginCodes 授权插件编码数组（一码多插件）
 *   tenantCode  绑定租户 code（sys_tenant.code，全局唯一）
 *   licensee    被授权方名称（公司主体，展示）
 *   issuedAt / notBefore / notAfter  ISO-8601 含时区偏移，如 2027-11-13T00:00:00+08:00
 * 可选字段：scopes（数据 scope 数组，须为各插件声明集子集）、extra（扩展对象）
 */
public class IssueLicense {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("用法: java IssueLicense.java <payload.json> <private-key.b64> <output.lic>");
            System.exit(1);
        }
        byte[] payloadBytes = canonicalize(new JsonParser(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)).parse())
                .getBytes(StandardCharsets.UTF_8);
        PrivateKey privateKey = loadPrivateKey(Files.readString(Path.of(args[1]), StandardCharsets.UTF_8).trim());

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payloadBytes);
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String payloadBase64 = Base64.getEncoder().encodeToString(payloadBytes);

        String license = "{\"payload\":\"" + payloadBase64 + "\",\"signature\":\"" + signature + "\"}";
        Files.writeString(Path.of(args[2]), license, StandardCharsets.UTF_8);

        System.out.println("签发完成 → " + args[2]);
        System.out.println("canonical payload（人工核对）:");
        System.out.println(new String(payloadBytes, StandardCharsets.UTF_8));
    }

    private static PrivateKey loadPrivateKey(String base64) throws Exception {
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    // ===== canonical JSON 序列化：对象 key 字典序、紧凑无空白；数字整数为 long 直出、小数保留字面 =====

    @SuppressWarnings("unchecked")
    static String canonicalize(Object value) {
        StringBuilder out = new StringBuilder();
        writeCanonical(value, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeCanonical(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof Double || value instanceof Float) {
            out.append(stripTrailingZero(((Number) value).doubleValue()));
        } else if (value instanceof Number n) {
            out.append(n.longValue());
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeCanonical(list.get(i), out);
            }
            out.append(']');
        } else if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = new TreeMap<>();
            raw.forEach((k, v) -> map.put(String.valueOf(k), v));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(entry.getKey(), out);
                out.append(':');
                writeCanonical(entry.getValue(), out);
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("不支持的字段类型: " + value.getClass().getName());
        }
    }

    private static String stripTrailingZero(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}

// ===== 极简递归下降 JSON 解析器（标准 JSON：对象/数组/字符串/数字/布尔/null）=====

class JsonParser {

    private final String text;
    private int pos;

    JsonParser(String text) {
        this.text = text;
    }

    Object parse() {
        Object value = parseValue();
        skipWhitespace();
        if (pos < text.length()) {
            throw error("JSON 结尾后存在多余内容");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw error("JSON 意外结束");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            map.put(key, parseValue());
            skipWhitespace();
            if (consume(',')) {
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (consume(',')) {
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("字符串未闭合");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("转义序列不完整");
            }
            char esc = text.charAt(pos++);
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("\\u 转义不完整");
                    }
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("非法转义字符 \\" + esc);
            }
        }
    }

    private Object parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (Character.isDigit(peek())) {
            pos++;
        }
        boolean isDecimal = false;
        if (peek() == '.') {
            isDecimal = true;
            pos++;
            while (Character.isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            isDecimal = true;
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            while (Character.isDigit(peek())) {
                pos++;
            }
        }
        String token = text.substring(start, pos);
        if (token.isEmpty() || token.equals("-")) {
            throw error("非法数字");
        }
        return isDecimal ? (Object) Double.parseDouble(token) : (Object) Long.parseLong(token);
    }

    private Object parseLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) {
            throw error("非法字面量");
        }
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private void expect(char expected) {
        if (pos >= text.length() || text.charAt(pos) != expected) {
            throw error("期望 '" + expected + "'");
        }
        pos++;
    }

    private boolean consume(char expected) {
        if (pos < text.length() && text.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON 解析失败（位置 " + pos + "）: " + message);
    }
}
