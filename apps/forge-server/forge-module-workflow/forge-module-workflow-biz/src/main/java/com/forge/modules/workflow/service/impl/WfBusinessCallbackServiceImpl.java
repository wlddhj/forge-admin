package com.forge.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.callback.CallbackDispatcher;
import com.forge.modules.workflow.callback.CallbackEventType;
import com.forge.modules.workflow.dto.callback.CallbackRecordResponse;
import com.forge.modules.workflow.dto.callback.CallbackRequest;
import com.forge.modules.workflow.dto.callback.CallbackResponse;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import com.forge.modules.workflow.service.WfBusinessCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 业务回调管理服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfBusinessCallbackServiceImpl implements WfBusinessCallbackService {

    private final WfBusinessCallbackMapper callbackMapper;
    private final WfCallbackRecordMapper recordMapper;
    private final CallbackDispatcher dispatcher;

    @Override
    public Page<CallbackResponse> pageCallback(int pageNum, int pageSize, String name, Integer enabled) {
        LambdaQueryWrapper<WfBusinessCallback> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(WfBusinessCallback::getName, name);
        }
        if (enabled != null) {
            wrapper.eq(WfBusinessCallback::getEnabled, enabled);
        }
        wrapper.orderByDesc(WfBusinessCallback::getId);
        Page<WfBusinessCallback> page = callbackMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<CallbackResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toResponse).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void createCallback(CallbackRequest request) {
        validateEvents(request.getEvents());
        WfBusinessCallback entity = new WfBusinessCallback();
        applyRequest(entity, request);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        callbackMapper.insert(entity);
        log.info("新增业务回调: id={}, name={}, url={}", entity.getId(), entity.getName(), entity.getUrl());
    }

    @Override
    public void updateCallback(CallbackRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(400, "回调ID不能为空");
        }
        WfBusinessCallback entity = callbackMapper.selectById(request.getId());
        if (entity == null) {
            throw new BusinessException(404, "回调配置不存在");
        }
        validateEvents(request.getEvents());
        applyRequest(entity, request);
        entity.setUpdateTime(LocalDateTime.now());
        callbackMapper.updateById(entity);
        log.info("更新业务回调: id={}", entity.getId());
    }

    @Override
    public void deleteCallback(Long id) {
        if (callbackMapper.selectById(id) == null) {
            throw new BusinessException(404, "回调配置不存在");
        }
        callbackMapper.deleteById(id);
        log.info("删除业务回调: id={}", id);
    }

    @Override
    public Page<CallbackRecordResponse> pageRecords(int pageNum, int pageSize, Long callbackId, String status) {
        LambdaQueryWrapper<WfCallbackRecord> wrapper = new LambdaQueryWrapper<>();
        if (callbackId != null) {
            wrapper.eq(WfCallbackRecord::getCallbackId, callbackId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(WfCallbackRecord::getStatus, status);
        }
        wrapper.orderByDesc(WfCallbackRecord::getId);
        Page<WfCallbackRecord> page = recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<CallbackRecordResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toRecordResponse).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void resendRecord(Long recordId) {
        WfCallbackRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(404, "投递记录不存在");
        }
        if ("SUCCESS".equals(record.getStatus())) {
            throw new BusinessException(400, "该记录已投递成功，无需重发");
        }
        WfBusinessCallback callback = callbackMapper.selectById(record.getCallbackId());
        if (callback == null || callback.getEnabled() == null || callback.getEnabled() != 1) {
            throw new BusinessException(400, "回调配置不存在或已禁用，无法重发");
        }
        dispatcher.deliver(callback, record.getEventType(), record.getProcessKey(),
                record.getBusinessKey(), record.getInstanceId(), record.getTaskId(),
                record.getNodeKey(), record.getPayload());
    }

    @Override
    public WfCallbackRecord getRecord(Long recordId) {
        return recordMapper.selectById(recordId);
    }

    private void validateEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            throw new BusinessException(400, "至少订阅一个事件");
        }
        List<String> valid = Arrays.stream(CallbackEventType.values()).map(Enum::name).toList();
        for (String e : events) {
            if (!valid.contains(e)) {
                throw new BusinessException(400, "非法事件类型：" + e + "（可选 " + String.join("/", valid) + "）");
            }
        }
    }

    private void applyRequest(WfBusinessCallback entity, CallbackRequest request) {
        entity.setName(request.getName());
        entity.setUrl(request.getUrl());
        entity.setSecret(request.getSecret());
        entity.setEvents(String.join(",", request.getEvents()));
        entity.setProcessKey(blankToNull(request.getProcessKey()));
        entity.setIncludeVariables(request.getIncludeVariables() == null ? 0 : request.getIncludeVariables());
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        entity.setTimeoutMs(request.getTimeoutMs() == null ? 5000 : request.getTimeoutMs());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private CallbackResponse toResponse(WfBusinessCallback entity) {
        CallbackResponse response = new CallbackResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setUrl(entity.getUrl());
        response.setSecret(entity.getSecret());
        response.setEvents(entity.getEvents() == null ? List.of()
                : Arrays.asList(entity.getEvents().split(",")));
        response.setProcessKey(entity.getProcessKey());
        response.setIncludeVariables(entity.getIncludeVariables());
        response.setEnabled(entity.getEnabled());
        response.setTimeoutMs(entity.getTimeoutMs());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private CallbackRecordResponse toRecordResponse(WfCallbackRecord record) {
        CallbackRecordResponse response = new CallbackRecordResponse();
        response.setId(record.getId());
        response.setCallbackId(record.getCallbackId());
        response.setCallbackName(record.getCallbackName());
        response.setEventType(record.getEventType());
        response.setProcessKey(record.getProcessKey());
        response.setBusinessKey(record.getBusinessKey());
        response.setInstanceId(record.getInstanceId());
        response.setTaskId(record.getTaskId());
        response.setNodeKey(record.getNodeKey());
        response.setPayload(record.getPayload());
        response.setHttpStatus(record.getHttpStatus());
        response.setResponseBody(record.getResponseBody());
        response.setStatus(record.getStatus());
        response.setRetryCount(record.getRetryCount());
        response.setErrorMsg(record.getErrorMsg());
        response.setCreateTime(record.getCreateTime());
        return response;
    }
}
