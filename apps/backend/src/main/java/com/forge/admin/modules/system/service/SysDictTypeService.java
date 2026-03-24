package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.system.dto.dict.DictTypeQueryRequest;
import com.forge.admin.modules.system.dto.dict.DictTypeRequest;
import com.forge.admin.modules.system.dto.dict.DictTypeResponse;

import java.util.List;

/**
 * 字典类型服务接口
 */
public interface SysDictTypeService {

    Page<DictTypeResponse> pageDictTypes(DictTypeQueryRequest request);

    List<DictTypeResponse> getAllDictTypes();

    DictTypeResponse getDictTypeDetail(Long id);

    void addDictType(DictTypeRequest request);

    void updateDictType(DictTypeRequest request);

    void deleteDictTypes(List<Long> ids);

    void updateStatus(Long id, Integer status);
}
