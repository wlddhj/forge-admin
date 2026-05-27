package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.workflow.dto.copy.CopyQueryRequest;
import com.forge.admin.modules.workflow.dto.copy.CopyResponse;
import com.forge.admin.modules.workflow.entity.WfProcessInstanceCopy;
import com.forge.admin.modules.workflow.identity.FlowableIdentityService;
import com.forge.admin.modules.workflow.mapper.WfProcessInstanceCopyMapper;
import com.forge.admin.modules.workflow.service.WfProcessInstanceCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WfProcessInstanceCopyServiceImpl implements WfProcessInstanceCopyService {

    private final WfProcessInstanceCopyMapper copyMapper;
    private final FlowableIdentityService flowableIdentityService;

    @Override
    public Page<CopyResponse> pageCopy(CopyQueryRequest request) {
        Page<WfProcessInstanceCopy> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<WfProcessInstanceCopy> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getProcessInstanceName()),
                        WfProcessInstanceCopy::getProcessInstanceName, request.getProcessInstanceName())
                .eq(request.getUserId() != null, WfProcessInstanceCopy::getUserId, request.getUserId())
                .orderByDesc(WfProcessInstanceCopy::getCreateTime);

        Page<WfProcessInstanceCopy> resultPage = copyMapper.selectPage(page, wrapper);

        // 批量获取用户名
        Set<Long> userIds = new HashSet<>();
        resultPage.getRecords().forEach(copy -> {
            userIds.add(copy.getStartUserId());
            userIds.add(copy.getUserId());
        });
        Map<Long, String> userNames = flowableIdentityService.getUserNames(userIds);

        Page<CopyResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream().map(copy -> {
            CopyResponse resp = new CopyResponse();
            resp.setId(copy.getId());
            resp.setStartUserId(copy.getStartUserId());
            resp.setStartUserName(userNames.getOrDefault(copy.getStartUserId(), ""));
            resp.setProcessInstanceName(copy.getProcessInstanceName());
            resp.setProcessInstanceId(copy.getProcessInstanceId());
            resp.setCategory(copy.getCategory());
            resp.setActivityId(copy.getActivityId());
            resp.setActivityName(copy.getActivityName());
            resp.setTaskId(copy.getTaskId());
            resp.setUserId(copy.getUserId());
            resp.setUserName(userNames.getOrDefault(copy.getUserId(), ""));
            resp.setReason(copy.getReason());
            resp.setCreateTime(copy.getCreateTime());
            return resp;
        }).collect(Collectors.toList()));

        return responsePage;
    }
}
