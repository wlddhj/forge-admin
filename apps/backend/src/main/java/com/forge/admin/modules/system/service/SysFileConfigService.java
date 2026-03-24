package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.file.FileConfigQueryRequest;
import com.forge.admin.modules.system.dto.file.FileConfigRequest;
import com.forge.admin.modules.system.dto.file.FileConfigResponse;
import com.forge.admin.modules.system.entity.SysFileConfig;

import java.util.List;

/**
 * 文件存储配置服务
 *
 * @author standadmin
 */
public interface SysFileConfigService extends IService<SysFileConfig> {

    /**
     * 分页查询配置
     */
    Page<FileConfigResponse> pageFileConfig(FileConfigQueryRequest request);

    /**
     * 获取配置详情
     */
    FileConfigResponse getFileConfigDetail(Long id);

    /**
     * 新增配置
     */
    void addFileConfig(FileConfigRequest request);

    /**
     * 更新配置
     */
    void updateFileConfig(FileConfigRequest request);

    /**
     * 删除配置
     */
    void deleteFileConfig(List<Long> ids);

    /**
     * 设置默认配置
     */
    void setDefaultConfig(Long id);

    /**
     * 获取默认配置
     */
    SysFileConfig getDefaultConfig();

    /**
     * 更新状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 测试连接
     */
    void testConnection(Long id);
}
