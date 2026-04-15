package com.forge.admin.modules.system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户导入结果 DTO
 *
 * @author standadmin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportResultDTO {

    /**
     * 创建成功的用户名列表
     */
    @Builder.Default
    private List<String> createUsernames = new ArrayList<>();

    /**
     * 更新成功的用户名列表
     */
    @Builder.Default
    private List<String> updateUsernames = new ArrayList<>();

    /**
     * 导入失败的用户，key 为用户名，value 为失败原因
     */
    @Builder.Default
    private Map<String, String> failureUsernames = new LinkedHashMap<>();
}
