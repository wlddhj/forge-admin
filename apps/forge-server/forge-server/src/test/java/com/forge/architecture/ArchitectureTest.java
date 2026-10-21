package com.forge.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层架构守护规则（依据 CLAUDE.md「后端架构 / 模块分层模式 / 多模块结构」）
 *
 * <p>放在启动模块 forge-server：其 classpath 聚合了全部业务模块与框架层，
 * 可对整体代码做包级依赖方向断言。规则分四组：
 * <ol>
 *     <li>模块内分层：controller → service → mapper 单向依赖，POJO 不反向依赖业务层</li>
 *     <li>模块间方向：system 是基础模块被他人依赖；同级业务模块默认互不依赖</li>
 *     <li>框架层纯净：common 最底层；framework 不得依赖业务模块（system 跨模块 api 除外）</li>
 *     <li>端点与命名：双端点 Controller 包位置、Controller/ServiceImpl/Mapper 命名后缀</li>
 * </ol>
 *
 * <p>已知的存量违规以显式排除方式冻结（见各规则 because 说明），防止耦合面继续扩大；
 * 修复存量后删除对应排除项即可收紧规则。
 */
@AnalyzeClasses(packages = "com.forge")
class ArchitectureTest {

    // ========== 一、模块内分层 ==========

    /** Controller 不得跨层直访 Mapper */
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_mappers =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .because("Controller 只能经 Service 访问数据");

    /** Mapper 是最底层，不得向上依赖 Service/Controller */
    @ArchTest
    static final ArchRule mappers_should_not_depend_on_upper_layers =
            noClasses().that().resideInAPackage("..mapper..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..");

    /** Service 不得依赖 Controller */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    /** 实体/DTO/VO/枚举是纯数据载体，不得依赖业务层 */
    @ArchTest
    static final ArchRule pojos_should_not_depend_on_business_layers =
            noClasses().that().resideInAnyPackage("..entity..", "..dto..", "..vo..", "..enums..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..mapper..");

    // ========== 二、模块间依赖方向 ==========

    /** system 是基础模块（被 workflow/ai/screen 依赖），不得反向依赖其他业务模块形成环 */
    @ArchTest
    static final ArchRule system_should_not_depend_on_other_biz_modules =
            noClasses().that().resideInAPackage("com.forge.modules.system..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.forge.modules.workflow..", "com.forge.modules.ai..", "com.forge.modules.screen..");

    /** ai 不得依赖同级模块（workflow/screen） */
    @ArchTest
    static final ArchRule ai_should_not_depend_on_sibling_modules =
            noClasses().that().resideInAPackage("com.forge.modules.ai..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.forge.modules.workflow..", "com.forge.modules.screen..");

    /** screen 不得依赖同级模块（workflow/ai） */
    @ArchTest
    static final ArchRule screen_should_not_depend_on_sibling_modules =
            noClasses().that().resideInAPackage("com.forge.modules.screen..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.forge.modules.workflow..", "com.forge.modules.ai..");

    /** workflow→ai 仅允许 AI 审批适配器这一处显式妥协，防止耦合面扩大 */
    @ArchTest
    static final ArchRule workflow_ai_coupling_is_limited_to_approval_adapter =
            noClasses().that().resideInAPackage("com.forge.modules.workflow..")
                    .and().doNotHaveFullyQualifiedName("com.forge.modules.workflow.service.impl.WfAiApprovalServiceImpl")
                    .should().dependOnClassesThat().resideInAPackage("com.forge.modules.ai..")
                    .because("workflow-biz pom 注释『AI 审批需要调用 AI 服务，暂时依赖 biz 层』；新增耦合应下沉至 ai-api 而非直依赖 ai-biz");

    // ========== 三、框架层纯净性 ==========

    /**
     * forge-common 是最底层公共库，不得依赖 framework 或任何业务模块。
     *
     * <p>仅约束 forge-common 独有的包（enumeration/enums/exception/json/response）：
     * system-biz 也复用了 com.forge.common.{config,utils,aspect} 包名放置横切类
     * （WebMvcConfig、DataScopeUtils、SecurityUtils 等），它们依赖 system 实体属合理行为；
     * utils 包两边都有、包级无法区分，该层纯净由 Maven 模块依赖兜底（forge-common 不依赖任何模块）。
     */
    @ArchTest
    static final ArchRule common_should_not_depend_on_framework_or_modules =
            noClasses().that().resideInAnyPackage(
                            "com.forge.common.enumeration..", "com.forge.common.enums..",
                            "com.forge.common.exception..", "com.forge.common.json..", "com.forge.common.response..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.forge.framework..", "com.forge.modules..");

    /** framework 不得依赖 workflow/ai/screen 业务模块 */
    @ArchTest
    static final ArchRule framework_should_not_depend_on_biz_modules =
            noClasses().that().resideInAPackage("com.forge.framework..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.forge.modules.workflow..", "com.forge.modules.ai..", "com.forge.modules.screen..");

    /** framework 对 system 的依赖仅开放跨模块 api 包（tenant starter 的 TenantApi） */
    @ArchTest
    static final ArchRule framework_should_only_touch_system_api =
            noClasses().that().resideInAPackage("com.forge.framework..")
                    .should().dependOnClassesThat(resideInAPackage("com.forge.modules.system..")
                            .and(not(resideInAPackage("com.forge.modules.system.api.."))))
                    .because("框架层只允许消费 system 暴露的跨模块接口（system.api 包），不得触碰业务实现");

    // ========== 四、端点位置与命名约定 ==========

    /** 双端点架构：Controller 必须落在 admin/app（自动注入 /admin-api、/app-api 前缀）或 uploads 静态资源包 */
    @ArchTest
    static final ArchRule rest_controllers_should_reside_in_endpoint_packages =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAnyPackage("..controller.admin..", "..controller.app..", "..controller.uploads..")
                    .because("WebMvcConfig 按包名 AntPath 匹配注入端点前缀，Controller 落在约定包外将丢失前缀");

    @ArchTest
    static final ArchRule rest_controllers_should_be_suffixed_controller =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().haveSimpleNameEndingWith("Controller");

    /** MyBatis Plus Mapper 接口统一以 Mapper 结尾 */
    @ArchTest
    static final ArchRule mappers_should_be_suffixed_mapper =
            classes().that().resideInAPackage("..mapper..")
                    .should().haveSimpleNameEndingWith("Mapper");

    /** Service 实现类统一以 Impl 结尾（排除匿名内部类） */
    @ArchTest
    static final ArchRule service_impls_should_be_suffixed_impl =
            classes().that().resideInAPackage("..service.impl..")
                    .and().areNotAnonymousClasses()
                    .should().haveSimpleNameEndingWith("Impl");
}
