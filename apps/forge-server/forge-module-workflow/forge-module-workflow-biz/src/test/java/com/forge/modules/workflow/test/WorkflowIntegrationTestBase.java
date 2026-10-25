package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * H2 端到端测试基类：每类执行前重建 schema（@Sql executionPhase BEFORE_TEST_METHOD 级由子类控制，
 * 此处用类级先建表），提供模型部署/发起/断言工具。
 */
@SpringBootTest(classes = WorkflowTestApplication.class)
@ActiveProfiles("test")
@Sql(scripts = "/schema-h2.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
public abstract class WorkflowIntegrationTestBase {

    @Autowired
    protected FlowLongEngine flowLongEngine;

    @Autowired
    protected QueryService queryService;

    @BeforeEach
    void setUpTenant() {
        // 租户拦截器要求上下文携带租户（与生产 JWT 过滤器行为对齐）
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDownTenant() {
        TenantContextHolder.clear();
    }

    protected FlowCreator user(long userId, String name) {
        return FlowCreatorFactory.user(userId, name);
    }

    /**
     * 部署一个模型 JSON，返回 processId
     */
    protected Long deploy(String modelJson) {
        return flowLongEngine.processService().deploy(
                new ByteArrayInputStream(modelJson.getBytes(StandardCharsets.UTF_8)),
                user(1L, "发起人甲"), true, process -> { });
    }

    /** 构造指定成员审批节点 */
    protected NodeModel approval(String key, String name, int examineMode, long... userIds) {
        NodeModel node = new NodeModel();
        node.setNodeKey(key);
        node.setNodeName(name);
        node.setType(1);
        node.setSetType(1);
        node.setExamineMode(examineMode);
        node.setNodeAssigneeList(java.util.Arrays.stream(userIds).mapToObj(id -> {
            NodeAssignee assignee = new NodeAssignee();
            assignee.setId(String.valueOf(id));
            assignee.setName(queryService == null ? "user" + id : userName(id));
            return assignee;
        }).toList());
        return node;
    }

    protected NodeModel startNode(String key) {
        NodeModel node = new NodeModel();
        node.setNodeKey(key);
        node.setNodeName("发起人");
        node.setType(0);
        return node;
    }

    @Autowired
    protected com.forge.modules.workflow.identity.FlowLongIdentityService identityService;

    protected String userName(long id) {
        return identityService.getUserName(id);
    }

    protected List<FlwTask> activeTasks(Long instanceId) {
        return queryService.getTasksByInstanceId(instanceId);
    }

    protected List<FlwTaskActor> actorsOf(Long taskId) {
        return queryService.getTaskActorsByTaskId(taskId);
    }
}
