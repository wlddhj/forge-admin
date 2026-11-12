package com.forge.modules.workflow.framework.task;

import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.forge.modules.workflow.entity.WfApprovalComment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务列表批量预取上下文：一次查询实例/流程/发起人名，替代转换链逐行查询。
 * <p>
 * 由 {@link TaskBatchLoader} 产出，供 {@link TaskResponseAssembler} 与列表查询消费；
 * 同包包私有承载，不进 api 模块。
 *
 * @author forge-admin
 */
class BatchContext {
    final Map<Long, FlwInstance> instanceMap = new HashMap<>();
    final Map<Long, FlwHisInstance> hisInstanceMap = new HashMap<>();
    final Map<Long, FlwProcess> processMap = new HashMap<>();
    final Map<Long, String> userNameMap = new HashMap<>();
    // his 转换专用（已办/抄送）
    final Map<Long, WfApprovalComment> latestCommentByTask = new HashMap<>();
    final Map<Long, List<FlwHisTask>> hisTasksByInstance = new HashMap<>();
    final Map<Long, List<FlwTask>> activeByParentTask = new HashMap<>();
}
