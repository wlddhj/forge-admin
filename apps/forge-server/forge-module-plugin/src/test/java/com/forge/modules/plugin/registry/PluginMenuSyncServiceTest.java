package com.forge.modules.plugin.registry;

import com.forge.common.exception.BusinessException;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.PluginMenuNode;
import com.forge.modules.plugin.TestDescriptors;
import com.forge.modules.system.entity.SysMenu;
import com.forge.modules.system.entity.SysRole;
import com.forge.modules.system.entity.SysRoleMenu;
import com.forge.modules.system.mapper.SysMenuMapper;
import com.forge.modules.system.mapper.SysRoleMapper;
import com.forge.modules.system.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 插件菜单 diff 同步测试：首次导入/幂等重跑/节点删除/声明校验（环、父键缺失）。
 * mock BaseMapper 时 insert 须回填自增 id（MP 单测坑）。
 *
 * @author forge-admin
 */
@DisplayName("插件菜单 diff 同步")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PluginMenuSyncServiceTest {

    @Mock
    private SysMenuMapper sysMenuMapper;

    @Mock
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    private PluginMenuSyncService service;

    private final AtomicLong idGenerator = new AtomicLong(100);

    private final List<SysMenu> dbMenus = new ArrayList<>();

    /** 当前目标声明集的 menuKey（孤儿集模拟过滤用，测试在 sync 前设置） */
    private final java.util.Set<String> targetKeys = new java.util.HashSet<>();

    @BeforeEach
    void setup() {
        service = new PluginMenuSyncService(sysMenuMapper, sysRoleMenuMapper, sysRoleMapper);
        // insert 回填自增 id 并落进"内存库"
        doAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            menu.setId(idGenerator.incrementAndGet());
            dbMenus.add(menu);
            return 1;
        }).when(sysMenuMapper).insert(any(SysMenu.class));
        // selectList 每轮 sync 固定两查（服务契约）：奇数次=存量集(eq pluginCode)、
        // 偶数次=孤儿集(eq pluginCode + notIn targetKeys)，按调用序模拟两种语义
        AtomicInteger selectSeq = new AtomicInteger();
        when(sysMenuMapper.selectList(any())).thenAnswer(invocation -> {
            if (selectSeq.incrementAndGet() % 2 == 1) {
                return new ArrayList<>(dbMenus);
            }
            return dbMenus.stream()
                    .filter(m -> !targetKeys.contains(m.getPluginMenuKey()))
                    .collect(java.util.stream.Collectors.toList());
        });
        doAnswer(invocation -> {
            dbMenus.removeIf(m -> m.getId().equals(invocation.getArgument(0)));
            return 1;
        }).when(sysMenuMapper).deleteById(any(Long.class));
    }

    private void mockSuperAdminRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("SUPER_ADMIN");
        role.setIsFixed(1);
        when(sysRoleMapper.selectOne(any())).thenReturn(role);
        when(sysRoleMenuMapper.selectCount(any())).thenReturn(0L);
    }

    /** 同步前刷新目标声明集（孤儿集模拟过滤依据） */
    private void syncWithTargets(com.forge.framework.plugin.descriptor.PluginDescriptor descriptor) {
        targetKeys.clear();
        targetKeys.addAll(descriptor.menus().stream()
                .map(PluginMenuNode::getMenuKey)
                .collect(java.util.stream.Collectors.toSet()));
        service.syncMenus(descriptor);
    }

    @Test
    @DisplayName("首次导入：按拓扑序插入全部节点并写归属标记与超管授权")
    void firstImport() {
        mockSuperAdminRole();
        syncWithTargets(TestDescriptors.demo());

        assertThat(dbMenus).hasSize(5);
        SysMenu root = dbMenus.stream().filter(m -> "demo-root".equals(m.getPluginMenuKey())).findFirst().orElseThrow();
        SysMenu project = dbMenus.stream().filter(m -> "demo-project".equals(m.getPluginMenuKey())).findFirst().orElseThrow();
        assertThat(root.getParentId()).isEqualTo(0L);
        assertThat(root.getPluginCode()).isEqualTo("demo");
        // 拓扑序保证：父先插，子的 parent_id 已解析
        assertThat(project.getParentId()).isEqualTo(root.getId());
        // 新节点自动授予超管
        verify(sysRoleMenuMapper, atLeastOnce()).insert(any(SysRoleMenu.class));
    }

    @Test
    @DisplayName("幂等重跑：存量完全一致时无新增无删除")
    void idempotentRerun() {
        mockSuperAdminRole();
        syncWithTargets(TestDescriptors.demo());
        int afterFirst = dbMenus.size();
        var insertCaptor = ArgumentCaptor.forClass(SysMenu.class);

        syncWithTargets(TestDescriptors.demo());
        assertThat(dbMenus).hasSize(afterFirst);
        // 第二次仍有 update（描述符可能变更字段），但不应再 insert
        verify(sysMenuMapper, atLeastOnce()).insert(insertCaptor.capture());
        assertThat(insertCaptor.getAllValues()).hasSize(afterFirst);
        assertThat(dbMenus.stream().map(SysMenu::getPluginMenuKey)).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("描述符删除节点：存量清理并连带清 sys_role_menu 关联")
    void removedNodeCleaned() {
        mockSuperAdminRole();
        syncWithTargets(TestDescriptors.demo());
        assertThat(dbMenus).hasSize(5);

        // 第二次同步：描述符去掉「立项撤销」节点
        List<PluginMenuNode> shrink = TestDescriptors.demo().menus().stream()
                .filter(node -> !"demo-project-edit".equals(node.getMenuKey()))
                .toList();
        syncWithTargets(TestDescriptors.withMenus(shrink));

        assertThat(dbMenus).hasSize(4);
        assertThat(dbMenus.stream().map(SysMenu::getPluginMenuKey)).doesNotContain("demo-project-edit");
        verify(sysRoleMenuMapper, atLeastOnce()).delete(any());
    }

    @Test
    @DisplayName("声明成环：拒绝同步")
    void cycleRejected() {
        List<PluginMenuNode> cycle = List.of(
                PluginMenuNode.builder().menuKey("a").parentKey("b").menuName("A").menuType(1).build(),
                PluginMenuNode.builder().menuKey("b").parentKey("a").menuName("B").menuType(1).build());
        assertThatThrownBy(() -> service.syncMenus(TestDescriptors.withMenus(cycle)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("声明无效");
    }

    @Test
    @DisplayName("parentKey 指向声明集外：拒绝同步")
    void unknownParentRejected() {
        List<PluginMenuNode> orphan = List.of(
                PluginMenuNode.builder().menuKey("a").parentKey("ghost").menuName("A").menuType(1).build());
        assertThatThrownBy(() -> service.syncMenus(TestDescriptors.withMenus(orphan)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("parentKey=ghost");
    }

    @Test
    @DisplayName("声明为空：清空该插件全部存量菜单")
    void emptyDeclarationClearsAll() {
        mockSuperAdminRole();
        syncWithTargets(TestDescriptors.demo());
        assertThat(dbMenus).isNotEmpty();

        syncWithTargets(TestDescriptors.withMenus(List.of()));
        assertThat(dbMenus).isEmpty();
    }

    @Test
    @DisplayName("无超管角色：导入成功但不自动授权（WARN 降级）")
    void noSuperAdminStillImports() {
        when(sysRoleMapper.selectOne(any())).thenReturn(null);
        syncWithTargets(TestDescriptors.demo());
        assertThat(dbMenus).hasSize(5);
        verify(sysRoleMenuMapper, never()).insert(any(SysRoleMenu.class));
    }

    @Test
    @DisplayName("menuKey 重复：拒绝同步")
    void duplicateKeyRejected() {
        List<PluginMenuNode> duplicated = List.of(
                PluginMenuNode.builder().menuKey("a").menuName("A").menuType(1).build(),
                PluginMenuNode.builder().menuKey("a").menuName("A2").menuType(1).build());
        assertThatThrownBy(() -> service.syncMenus(TestDescriptors.withMenus(duplicated)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("menuKey 重复");
    }

    // ===== 测试描述符工具见 TestDescriptors =====
}
