package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.listener.ListenerQueryRequest;
import com.forge.modules.workflow.dto.listener.ListenerRequest;
import com.forge.modules.workflow.dto.listener.ListenerResponse;

import java.util.List;

public interface WfProcessListenerService {

    Page<ListenerResponse> pageListeners(ListenerQueryRequest request);

    List<ListenerResponse> listAllEnabled();

    ListenerResponse getListenerDetail(Long id);

    void addListener(ListenerRequest request);

    void updateListener(ListenerRequest request);

    void deleteListeners(List<Long> ids);
}
