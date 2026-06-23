package com.forge.modules.workflow.service.impl;

import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 流程抄送服务实现 - FlowLong 版本
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessInstanceCopyServiceImpl implements WfProcessInstanceCopyService {

    @Override
    public void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason) {
        // TODO: 实现自动抄送逻辑
        // 1. 根据 wf_process_ext.auto_copy_strategy 获取抄送策略
        // 2. 根据 auto_copy_param 计算抄送用户
        // 3. 创建抄送记录
        log.info("流程结束自动抄送：instanceId={}, definitionId={}, reason={}",
                processInstanceId, processDefinitionId, reason);
    }

    @Override
    public void manualCopy(String processInstanceId, String[] copyUserIds, String reason) {
        // TODO: 实现手动抄送逻辑
        log.info("手动抄送：instanceId={}, copyUserIds={}, reason={}",
                processInstanceId, copyUserIds, reason);
    }
}