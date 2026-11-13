package com.forge.modules.demo.plugin;

import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.PluginMenuNode;
import com.forge.framework.plugin.descriptor.ScopeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * demo 插件描述符——第一个标准编译期模块插件（契约试金石）。
 *
 * <p>启动时由插件注册 Runner 扫描：元数据入库 sys_plugin、菜单按 menuKey
 * 幂等导入 sys_menu（存量 280-284 菜单由升级 SQL 标记归属后无缝接管）。
 *
 * @author forge-admin
 */
@Component
public class DemoPluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_CODE = "demo";

    @Override
    public String pluginCode() {
        return PLUGIN_CODE;
    }

    @Override
    public String name() {
        return "业务示例插件";
    }

    @Override
    public String description() {
        return "项目立项接入工作流示例（原 forge-module-demo 模块，作为插件体系的标准示范）";
    }

    @Override
    public String provider() {
        return "forge-admin";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public List<PluginMenuNode> menus() {
        return List.of(
                PluginMenuNode.builder()
                        .menuKey("demo-root").parentKey(null)
                        .menuName("示例模块").menuType(0)
                        .routePath("/demo").componentPath("Layout").redirectPath("/demo/project")
                        .icon("Folder").sortOrder(9)
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("demo-project").parentKey("demo-root")
                        .menuName("项目立项").menuType(1)
                        .routePath("/demo/project").componentPath("/views/demo/project/index")
                        .icon("DocumentChecked").sortOrder(1)
                        .permission("demo:project:list")
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("demo-project-query").parentKey("demo-project")
                        .menuName("立项查询").menuType(2)
                        .sortOrder(1).permission("demo:project:query")
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("demo-project-add").parentKey("demo-project")
                        .menuName("立项提交").menuType(2)
                        .sortOrder(2).permission("demo:project:add")
                        .build(),
                PluginMenuNode.builder()
                        .menuKey("demo-project-edit").parentKey("demo-project")
                        .menuName("立项撤销").menuType(2)
                        .sortOrder(3).permission("demo:project:edit")
                        .build()
        );
    }

    @Override
    public List<ScopeDefinition> declaredScopes() {
        return List.of(
                ScopeDefinition.readOnly("user:read", "读员工数据", "经数据门面查询员工列表与部门归属（默认放行）"),
                ScopeDefinition.readOnly("dept:read", "读组织数据", "经数据门面查询组织树（默认放行）"),
                ScopeDefinition.writable("user:write", "写员工数据", "经数据门面调整员工部门等写操作（须勾选授予）")
        );
    }
}
