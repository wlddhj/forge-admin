package com.forge.admin.modules.system.dto;

import lombok.Data;

@Data
public class DashboardStats {
    private Long userCount;
    private Long roleCount;
    private Long menuCount;
    private Long logCount;
    private Long deptCount;
    private Long positionCount;
    private Long dictCount;
    private Long configCount;
}
