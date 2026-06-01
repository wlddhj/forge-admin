package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.modules.system.dto.keysequence.KeySequenceQueryRequest;
import com.forge.modules.system.dto.keysequence.KeySequenceRequest;
import com.forge.modules.system.dto.keysequence.KeySequenceResponse;
import com.forge.modules.system.entity.KeySequence;

public interface KeySequenceService extends IService<KeySequence> {

    String getNextKey(String category);

    String getNextKey(String category, String arg0);

    Page<KeySequenceResponse> pageKeySequences(KeySequenceQueryRequest request);

    KeySequenceResponse getKeySequenceDetail(Long id);

    void addKeySequence(KeySequenceRequest request);

    void updateKeySequence(KeySequenceRequest request);

    void deleteKeySequences(Long[] ids);
}
