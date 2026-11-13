package com.forge.framework.plugin.data;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 插件视角的组织节点（树形返回）。
 *
 * @author forge-admin
 */
@Data
@Builder
public class PluginDeptDTO {

    private Long id;

    private Long parentId;

    private String deptName;

    private String leader;

    private String phone;

    private String email;

    private Integer sortOrder;

    /** 1启用 0禁用 */
    private Integer status;

    private List<PluginDeptDTO> children;
}
