package com.forge.modules.workflow.framework.validator;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 流程模型校验器单元测试（approval/cc/conditionNode 三段 setType 校验合并后的文案前缀回归）
 */
class WfProcessModelValidatorTest {

    private WfProcessModelValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WfProcessModelValidator(mock(ProcessService.class));
    }

    private NodeModel node(String key, String name, int type) {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey(key);
        nodeModel.setNodeName(name);
        nodeModel.setType(type);
        NodeModel root = new NodeModel();
        root.setNodeKey("start");
        root.setNodeName("发起人");
        root.setType(0);
        root.setChildNode(nodeModel);
        return root;
    }

    @Test
    @DisplayName("审批节点指定成员未配置处理人：审批前缀文案")
    void approval_prefixUnconfiguredAssignee() {
        NodeModel root = node("ap", "审批A", 1);
        root.getChildNode().setSetType(1); // specifyMembers

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(root, Collections.emptySet()));
        assertEquals("审批节点「审批A」未配置处理人员", ex.getMessage());
    }

    @Test
    @DisplayName("审批节点未选择角色：审批前缀文案")
    void approval_prefixRoleMissing() {
        NodeModel root = node("ap", "审批B", 1);
        root.getChildNode().setSetType(3); // role

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(root, Collections.emptySet()));
        assertEquals("审批节点「审批B」未选择角色", ex.getMessage());
    }

    @Test
    @DisplayName("抄送节点指定成员未配置处理人：抄送前缀文案")
    void cc_prefixUnconfiguredAssignee() {
        NodeModel root = node("cc", "抄送A", 2);
        root.getChildNode().setSetType(1); // specifyMembers

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(root, Collections.emptySet()));
        assertEquals("抄送节点「抄送A」未配置处理人员", ex.getMessage());
    }

    @Test
    @DisplayName("抄送节点允许自选时指定成员不校验，role 仍校验")
    void cc_allowSelectionExemptOnlyForSpecifyMembers() {
        NodeModel root = node("cc", "抄送B", 2);
        root.getChildNode().setSetType(1);
        root.getChildNode().setAllowSelection(Boolean.TRUE);
        assertDoesNotThrow(() -> validator.checkNodeModelWithDynamicAssignee(root, Collections.emptySet()));

        NodeModel roleRoot = node("cc2", "抄送C", 2);
        roleRoot.getChildNode().setSetType(3);
        roleRoot.getChildNode().setAllowSelection(Boolean.TRUE);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(roleRoot, Collections.emptySet()));
        assertEquals("抄送节点「抄送C」未选择角色", ex.getMessage());
    }

    @Test
    @DisplayName("条件审批节点未配置处理人：条件审批前缀文案；未配置条件列表文案保留")
    void conditionNode_prefixAndConditionList() {
        NodeModel root = node("cd", "条件A", 3);
        root.getChildNode().setSetType(1); // specifyMembers

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(root, Collections.emptySet()));
        assertEquals("条件审批节点「条件A」未配置处理人员", ex.getMessage());

        NodeModel withAssignee = node("cd2", "条件B", 3);
        withAssignee.getChildNode().setSetType(1);
        NodeAssignee assignee = new NodeAssignee();
        assignee.setId("1");
        withAssignee.getChildNode().setNodeAssigneeList(List.of(assignee));
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(withAssignee, Collections.emptySet()));
        assertEquals("条件审批节点「条件B」未配置条件列表", ex2.getMessage());
    }

    @Test
    @DisplayName("发起人自选：审批/抄送前缀文案不同；已传递审批人时放行")
    void initiatorSelected_prefixByNodeType() {
        NodeModel approvalRoot = node("ap", "审批C", 1);
        approvalRoot.getChildNode().setSetType(4); // initiatorSelected
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(approvalRoot, Collections.emptySet()));
        assertEquals("发起人自选节点「审批C」未选择审批人", ex.getMessage());

        NodeModel ccRoot = node("cc", "抄送D", 2);
        ccRoot.getChildNode().setSetType(4);
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> validator.checkNodeModelWithDynamicAssignee(ccRoot, Collections.emptySet()));
        assertEquals("抄送发起人自选节点「抄送D」未选择审批人", ex2.getMessage());

        // 发起人自选节点已通过 FlowDataTransfer 传递审批人：放行
        assertDoesNotThrow(() -> validator.checkNodeModelWithDynamicAssignee(approvalRoot, java.util.Set.of("ap")));
    }

    @Test
    @DisplayName("审批节点已配置处理人时跳过 setType 校验")
    void approval_configuredAssigneePasses() {
        NodeModel root = node("ap", "审批D", 1);
        NodeAssignee assignee = new NodeAssignee();
        assignee.setId("1");
        root.getChildNode().setNodeAssigneeList(List.of(assignee));
        assertDoesNotThrow(() -> validator.checkNodeModelWithDynamicAssignee(root, Collections.emptySet()));
    }
}
