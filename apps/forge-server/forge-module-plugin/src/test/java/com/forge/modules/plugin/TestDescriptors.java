package com.forge.modules.plugin;

import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.PluginMenuNode;
import com.forge.framework.plugin.descriptor.ScopeDefinition;

import java.util.List;

/**
 * 测试用插件描述符工具。
 *
 * @author forge-admin
 */
public final class TestDescriptors {

    private TestDescriptors() {
    }

    /** 与 forge-module-demo 的 DemoPluginDescriptor 同构（不跨模块依赖） */
    public static PluginDescriptor demo() {
        return new PluginDescriptor() {
            @Override
            public String pluginCode() {
                return "demo";
            }

            @Override
            public String name() {
                return "业务示例插件";
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public List<PluginMenuNode> menus() {
                return List.of(
                        PluginMenuNode.builder().menuKey("demo-root").menuName("示例模块").menuType(0)
                                .routePath("/demo").componentPath("Layout").sortOrder(9).build(),
                        PluginMenuNode.builder().menuKey("demo-project").parentKey("demo-root")
                                .menuName("项目立项").menuType(1).routePath("/demo/project")
                                .componentPath("/views/demo/project/index").sortOrder(1)
                                .permission("demo:project:list").build(),
                        PluginMenuNode.builder().menuKey("demo-project-query").parentKey("demo-project")
                                .menuName("立项查询").menuType(2).sortOrder(1).permission("demo:project:query").build(),
                        PluginMenuNode.builder().menuKey("demo-project-add").parentKey("demo-project")
                                .menuName("立项提交").menuType(2).sortOrder(2).permission("demo:project:add").build(),
                        PluginMenuNode.builder().menuKey("demo-project-edit").parentKey("demo-project")
                                .menuName("立项撤销").menuType(2).sortOrder(3).permission("demo:project:edit").build());
            }

            @Override
            public List<ScopeDefinition> declaredScopes() {
                return List.of(
                        ScopeDefinition.readOnly("user:read", "读员工数据", ""),
                        ScopeDefinition.readOnly("dept:read", "读组织数据", ""),
                        ScopeDefinition.writable("user:write", "写员工数据", ""));
            }
        };
    }

    /** 覆盖菜单声明的描述符（构造异常场景用） */
    public static PluginDescriptor withMenus(List<PluginMenuNode> menus) {
        return new PluginDescriptor() {
            @Override
            public String pluginCode() {
                return "demo";
            }

            @Override
            public String name() {
                return "业务示例插件";
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public List<PluginMenuNode> menus() {
                return menus;
            }
        };
    }
}
