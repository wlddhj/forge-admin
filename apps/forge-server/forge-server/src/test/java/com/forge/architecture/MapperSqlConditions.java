package com.forge.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ArchitectureTest「五、Mapper SQL 落位」的条件实现。
 *
 * <p>mapper 口径 = 带 {@code @Mapper} 的接口，与 {@code @MapperScan(annotationClass = Mapper.class)} 的运行时注册口径一致。
 * XML 语句索引扫 {@code classpath*:mapper/**}{@code /*.xml}（同应用 mybatis-plus.mapper-locations），
 * 只读根节点 {@code <mapper namespace>} 下直接子元素 select/insert/update/delete 的 id。
 */
final class MapperSqlConditions {

    /** 注解 SQL 长度上限：{@code     @Select("} + SQL + {@code ")} 在 120 列内放得下，再长就会被拆成多行拼接，应写进 XML */
    static final int MAX_ANNOTATION_SQL_LENGTH = 100;

    private static final String ANNOTATION_PACKAGE = "org.apache.ibatis.annotations.";
    private static final List<String> LITERAL_SQL_ANNOTATIONS = List.of("Select", "Insert", "Update", "Delete");
    private static final List<String> STATEMENT_TAGS = List.of("select", "insert", "update", "delete");
    /** 绑定 SQL 的全部注解：字面量四种 + Provider 四种，及各自的 @Repeatable 容器 */
    private static final Set<String> SQL_ANNOTATIONS = new HashSet<>();

    static {
        for (String name : LITERAL_SQL_ANNOTATIONS) {
            for (String type : List.of(name, name + "Provider")) {
                SQL_ANNOTATIONS.add(ANNOTATION_PACKAGE + type);
                SQL_ANNOTATIONS.add(ANNOTATION_PACKAGE + type + "$List");
            }
        }
    }

    private static Map<String, Set<String>> xmlStatements;

    private MapperSqlConditions() {
    }

    /** G1：接口声明的每个抽象方法须有 SQL 注解或 XML 同 id 语句（default/static/合成方法与继承自 BaseMapper 的方法不计） */
    static ArchCondition<JavaClass> bindEveryAbstractMethodToSql() {
        return new ArchCondition<>("bind every abstract method to SQL annotation or XML statement") {
            @Override
            public void check(JavaClass mapper, ConditionEvents events) {
                Set<String> xmlIds = xmlStatements().getOrDefault(mapper.getName(), Set.of());
                for (JavaMethod method : mapper.getMethods()) {
                    if (isDeclaredAbstract(method) && !hasSqlAnnotation(method) && !xmlIds.contains(method.getName())) {
                        events.add(SimpleConditionEvent.violated(method,
                                describe(method) + " 未绑定 SQL：既无 @Select 等注解，XML 中也无同 id 语句"));
                    }
                }
            }
        };
    }

    /** G2：namespace 已有 XML 的 mapper，方法不得再带 SQL 注解 */
    static ArchCondition<JavaClass> notMixXmlWithSqlAnnotations() {
        return new ArchCondition<>("not mix XML statements with SQL annotations") {
            @Override
            public void check(JavaClass mapper, ConditionEvents events) {
                if (!xmlStatements().containsKey(mapper.getName())) {
                    return;
                }
                for (JavaMethod method : mapper.getMethods()) {
                    if (hasSqlAnnotation(method)) {
                        events.add(SimpleConditionEvent.violated(method,
                                describe(method) + " 所在 mapper 已有 XML，SQL 注解应并入 XML"));
                    }
                }
            }
        };
    }

    /** G3：@Select/@Insert/@Update/@Delete 只许单段、单行、无 {@code <script>}、不超长的简单 SQL */
    static ArchCondition<JavaMethod> useOnlySingleLineSimpleAnnotationSql() {
        return new ArchCondition<>("use only single-line simple SQL in annotations") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
                    String type = annotation.getRawType().getName();
                    if (!type.startsWith(ANNOTATION_PACKAGE)
                            || !LITERAL_SQL_ANNOTATIONS.contains(type.substring(ANNOTATION_PACKAGE.length()))) {
                        continue;
                    }
                    Object value = annotation.get("value").orElse("");
                    String[] parts = value instanceof Object[] array
                            ? Arrays.stream(array).map(String::valueOf).toArray(String[]::new)
                            : new String[]{String.valueOf(value)};
                    String sql = String.join(" ", parts);
                    String reason = parts.length > 1 ? "多段数组拼接"
                            : sql.contains("<script>") ? "含 <script> 动态 SQL"
                            : sql.contains("\n") || sql.contains("\r") ? "多行文本"
                            : sql.length() > MAX_ANNOTATION_SQL_LENGTH ? "长度 " + sql.length() + " 超过 " + MAX_ANNOTATION_SQL_LENGTH
                            : null;
                    if (reason != null) {
                        events.add(SimpleConditionEvent.violated(method, describe(method) + " 的 @"
                                + annotation.getRawType().getSimpleName() + " 应迁入 mapper XML（" + reason + "）"));
                    }
                }
            }
        };
    }

    private static boolean isDeclaredAbstract(JavaMethod method) {
        Set<JavaModifier> modifiers = method.getModifiers();
        return modifiers.contains(JavaModifier.ABSTRACT)
                && !modifiers.contains(JavaModifier.SYNTHETIC) && !modifiers.contains(JavaModifier.BRIDGE);
    }

    private static boolean hasSqlAnnotation(JavaMethod method) {
        return method.getAnnotations().stream().anyMatch(a -> SQL_ANNOTATIONS.contains(a.getRawType().getName()));
    }

    private static String describe(JavaMethod method) {
        return method.getOwner().getSimpleName() + "." + method.getName();
    }

    private static synchronized Map<String, Set<String>> xmlStatements() {
        if (xmlStatements == null) {
            xmlStatements = loadXmlStatements();
        }
        return xmlStatements;
    }

    private static Map<String, Set<String>> loadXmlStatements() {
        Map<String, Set<String>> index = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // mapper.xml 声明了 mybatis DTD，关闭外部 DTD 加载，避免测试联网
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
                Element root;
                try (InputStream in = resource.getInputStream()) {
                    root = builder.parse(in).getDocumentElement();
                }
                if (!"mapper".equals(root.getTagName())) {
                    continue;
                }
                Set<String> ids = index.computeIfAbsent(root.getAttribute("namespace"), k -> new HashSet<>());
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = children.item(i);
                    if (node instanceof Element element && STATEMENT_TAGS.contains(element.getTagName())) {
                        ids.add(element.getAttribute("id"));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("扫描 classpath*:mapper/**/*.xml 失败", e);
        }
        return index;
    }
}
