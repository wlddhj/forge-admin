package com.forge.admin.modules.system.dto.user;

import lombok.Data;

/**
 * 用户查询请求
 *
 * @author standadmin
 */
@Data
public class UserQueryRequest {

    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String username;
    private String nickname;
    private String phone;
    private Integer status;
    private Long deptId;

    /** 数据权限SQL片段（由切面自动填充） */
    private String dataScope;
}
