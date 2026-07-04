package com.forge.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 大屏与数据源关联实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_screen_data_source_ref")
public class SysScreenDataSourceRef {

    /**
     * 大屏 ID
     */
    private Long screenId;

    /**
     * 数据源 ID
     */
    private Long dataSourceId;
}
