package com.forge.modules.contract.dto;

import com.forge.modules.contract.entity.CtCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同分类 DTO 集
 *
 * @author forge-admin
 */
public class CategoryDtos {

    private CategoryDtos() {
    }

    /** 新建/编辑分类 */
    @Data
    public static class SaveRequest {
        /** 编辑时必填 */
        private Long id;

        @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 100, message = "分类名称最长 100 字")
        private String categoryName;

        @Schema(description = "分类编码（字母数字，租户内唯一；叶子编码参与合同编号）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "分类编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$", message = "分类编码仅限 1-32 位字母数字下划线")
        private String categoryCode;

        @Schema(description = "父分类ID（0=根）")
        private Long parentId;

        private Integer sortOrder;

        /** 0:停用 1:启用 */
        private Integer status;

        @Size(max = 255, message = "备注最长 255 字")
        private String remark;
    }

    /** 树节点（list 树/表单下拉共用） */
    @Data
    public static class TreeNode {
        private Long id;
        private String categoryName;
        private String categoryCode;
        private Long parentId;
        private Integer sortOrder;
        private Integer status;
        private String remark;
        private LocalDateTime updateTime;
        /** 是否叶子（无子节点；仅启用叶子可挂模板） */
        private boolean leaf;
        private List<TreeNode> children;

        public static TreeNode from(CtCategory c, boolean leaf) {
            TreeNode n = new TreeNode();
            n.setId(c.getId());
            n.setCategoryName(c.getCategoryName());
            n.setCategoryCode(c.getCategoryCode());
            n.setParentId(c.getParentId());
            n.setSortOrder(c.getSortOrder());
            n.setStatus(c.getStatus());
            n.setRemark(c.getRemark());
            n.setUpdateTime(c.getUpdateTime());
            n.setLeaf(leaf);
            return n;
        }
    }

}
