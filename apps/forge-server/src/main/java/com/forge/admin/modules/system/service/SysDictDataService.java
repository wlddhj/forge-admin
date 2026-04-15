package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.system.dto.dict.DictDataQueryRequest;
import com.forge.admin.modules.system.dto.dict.DictDataRequest;
import com.forge.admin.modules.system.dto.dict.DictDataResponse;

import java.util.List;

/**
 * 字典数据服务接口
 */
public interface SysDictDataService {

    Page<DictDataResponse> pageDictData(DictDataQueryRequest request);

    List<DictDataResponse> getDictDataByType(String dictType);

    DictDataResponse getDictDataDetail(Long id);

    void addDictData(DictDataRequest request);

    void updateDictData(DictDataRequest request);

    void deleteDictData(List<Long> ids);

    void updateStatus(Long id, Integer status);

    void refreshCache();
}
