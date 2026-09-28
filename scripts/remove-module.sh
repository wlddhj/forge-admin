#!/bin/bash
#
# 删除 Maven 模块脚本
# 用法: ./scripts/remove-module.sh [模块名称]
# 不带参数时会列出可删除的模块供选择
# 示例: ./scripts/remove-module.sh workflow
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SERVER_DIR="$PROJECT_ROOT/apps/forge-server"

# 从 pom 中删除指定 artifactId 的整个 <dependency> 块（含 <dependency> 开标签，保持 XML 结构完整）
# sed 逻辑：从 <dependency> 行起累积整块至 </dependency>，若块内含目标 artifactId 则整块删除
remove_dep_block() {
    local pom="$1" artifact_id="$2"
    [ -f "$pom" ] || return 1
    grep -q "<artifactId>$artifact_id</artifactId>" "$pom" || return 1
    sed -i.bak \
        -e '/<dependency>/{' \
        -e ':a' \
        -e 'N' \
        -e '/<\/dependency>/!ba' \
        -e "/<artifactId>$artifact_id<\/artifactId>/d" \
        -e '}' \
        "$pom"
    rm -f "$pom.bak"
    echo "  ✓ 已移除 ${pom#$SERVER_DIR/} 中的 $artifact_id"
    return 0
}

# 交互确认：整行读取（避免 read -n 1 的换行残留污染下一次读取）
confirm() {
    local reply
    read -r -p "$1" reply
    [[ $reply =~ ^[Yy]$ ]]
}

# 读取模块 pom 中的 <description>
module_desc() {
    local pom="$SERVER_DIR/forge-module-$1/pom.xml"
    if [ -f "$pom" ]; then
        grep -m1 '<description>' "$pom" | sed 's/.*<description>\(.*\)<\/description>.*/\1/' || true
    fi
}

# 列出可删除的模块并让用户选择（编号或名称），结果写入 MODULE_NAME
select_module() {
    local modules=() dir name
    for dir in "$SERVER_DIR"/forge-module-*/; do
        [ -d "$dir" ] || continue
        name="$(basename "$dir")"
        modules+=("${name#forge-module-}")
    done

    if [ ${#modules[@]} -eq 0 ]; then
        echo "错误: $SERVER_DIR 下未发现 forge-module-* 模块"
        exit 1
    fi

    echo "可删除的业务模块:"
    echo ""
    local i mark
    for i in "${!modules[@]}"; do
        mark=""
        if [ "${modules[$i]}" = "system" ]; then
            mark="  [核心模块，删除后系统无法运行]"
        fi
        printf "  %d) %-10s %s%s\n" "$((i+1))" "${modules[$i]}" "$(module_desc "${modules[$i]}")" "$mark"
    done
    echo ""
    read -r -p "请输入要删除的模块（编号或名称）: " choice

    MODULE_NAME=""
    if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "${#modules[@]}" ]; then
        MODULE_NAME="${modules[$((choice-1))]}"
    else
        local m
        for m in "${modules[@]}"; do
            if [ "$m" = "$choice" ]; then
                MODULE_NAME="$m"
                break
            fi
        done
    fi

    if [ -z "$MODULE_NAME" ]; then
        echo "错误: 无效选择: $choice"
        exit 1
    fi
}

# 无参数时列出模块供选择，有参数时直接使用
MODULE_NAME="$1"
if [ -z "$MODULE_NAME" ]; then
    select_module
    echo ""
fi

MODULE_DIR="$SERVER_DIR/forge-module-$MODULE_NAME"

# 检查模块是否存在
if [ ! -d "$MODULE_DIR" ]; then
    echo "错误: 模块目录不存在: $MODULE_DIR"
    exit 1
fi

# 核心模块二次确认
if [ "$MODULE_NAME" = "system" ]; then
    echo "警告: system 是核心模块（用户/角色/菜单/认证/租户均在其中），删除后系统无法运行！"
    if ! confirm "确认仍要删除? (y/N): "; then
        echo "已取消"
        exit 0
    fi
    echo ""
fi

echo "=== 删除模块: $MODULE_NAME ==="
echo ""

# 1. 从根 pom.xml 移除模块引用
echo "[1/7] 从根 pom.xml 移除模块引用..."
ROOT_POM="$SERVER_DIR/pom.xml"
if grep -q "<module>forge-module-$MODULE_NAME</module>" "$ROOT_POM"; then
    sed -i.bak "/<module>forge-module-$MODULE_NAME<\/module>/d" "$ROOT_POM"
    rm -f "$ROOT_POM.bak"
    echo "  ✓ 已从根 pom.xml 移除 forge-module-$MODULE_NAME"
else
    echo "  - 根 pom.xml 中未找到该模块引用"
fi

# 2. 从 forge-dependencies 移除依赖声明
echo "[2/7] 从 forge-dependencies 移除依赖声明..."
DEPS_POM="$SERVER_DIR/forge-dependencies/pom.xml"
if [ -f "$DEPS_POM" ]; then
    remove_dep_block "$DEPS_POM" "forge-module-$MODULE_NAME-api" || echo "  - 未找到 forge-module-$MODULE_NAME-api 依赖声明"
    remove_dep_block "$DEPS_POM" "forge-module-$MODULE_NAME-biz" || echo "  - 未找到 forge-module-$MODULE_NAME-biz 依赖声明"
else
    echo "  - forge-dependencies/pom.xml 不存在"
fi

# 3. 从 forge-server 移除依赖引用
echo "[3/7] 从 forge-server 移除依赖引用..."
SERVER_POM="$SERVER_DIR/forge-server/pom.xml"
if [ -f "$SERVER_POM" ]; then
    remove_dep_block "$SERVER_POM" "forge-module-$MODULE_NAME-biz" || echo "  - 未找到 forge-module-$MODULE_NAME-biz 依赖"
else
    echo "  - forge-server/pom.xml 不存在"
fi

# 4. 检查其他模块的依赖并移除（整个 dependency 块，避免破坏 XML 结构）
echo "[4/7] 检查其他模块的依赖..."
while IFS= read -r other_pom; do
    remove_dep_block "$other_pom" "forge-module-$MODULE_NAME-api" || true
    remove_dep_block "$other_pom" "forge-module-$MODULE_NAME-biz" || true
done < <(find "$SERVER_DIR" -name "pom.xml" -not -path "$MODULE_DIR/*")

# 5. 删除数据库迁移脚本（可选）
echo "[5/7] 检查数据库迁移脚本..."
# 扫描 forge-server 下所有 db/migration 目录
# （兼容单仓库统一目录 + 子模块独立目录两种模式：
#   forge-server/src/main/resources/db/migration/  与
#   forge-module-X/forge-module-X-biz/src/main/resources/db/migration/）
# 排除 target/ 构建产物目录
# 匹配规则：位于模块自身目录下，或文件名含完整模块名（下划线分词，
# 避免内容级子串匹配的误报，如 "ai" 误匹配 "email"）；
# workflow 模块的迁移文件另使用 wf_ 前缀（如 V2026052701__wf_expression_listener.sql）
is_module_migration() {
    local f="$1" base
    case "$f" in
        "$MODULE_DIR"/*) return 0 ;;
    esac
    base="$(basename "$f")"
    if printf '%s' "$base" | grep -Eq "(^|_)$MODULE_NAME(_|$)"; then
        return 0
    fi
    if [ "$MODULE_NAME" = "workflow" ] && printf '%s' "$base" | grep -Eq "(^|_)wf(_|$)"; then
        return 0
    fi
    return 1
}
migration_files=""
while IFS= read -r f; do
    if is_module_migration "$f"; then
        migration_files+="$f"$'\n'
    fi
done < <(find "$SERVER_DIR" -path "*/db/migration/*.sql" -not -path "*/target/*")
if [ -n "$migration_files" ]; then
    echo "  发现相关迁移脚本:"
    printf '%s' "$migration_files" | sed "s|^$SERVER_DIR/|  |"
    if confirm "  是否删除这些迁移脚本? (y/N): "; then
        # 逐行删除（不使用 xargs -d，BSD xargs/macOS 不支持）
        while IFS= read -r f; do
            [ -n "$f" ] || continue
            rm -f "$f"
            echo "  ✓ 已删除 ${f#$SERVER_DIR/}"
        done <<< "$migration_files"
    else
        echo "  - 跳过删除迁移脚本"
    fi
else
    echo "  - 未发现相关迁移脚本"
fi

# 6. 清理前端相关文件（可选）
echo "[6/7] 清理前端相关文件..."
FRONTEND_DIR="$PROJECT_ROOT/apps/forge-web/src"
frontend_paths=()
for p in "$FRONTEND_DIR/views/$MODULE_NAME" "$FRONTEND_DIR/api/$MODULE_NAME"; do
    [ -e "$p" ] && frontend_paths+=("$p")
done
if [ ${#frontend_paths[@]} -gt 0 ]; then
    echo "  发现前端相关目录:"
    printf '    %s\n' "${frontend_paths[@]#$PROJECT_ROOT/}"
    if confirm "  是否删除这些前端文件? (y/N): "; then
        rm -rf "${frontend_paths[@]}"
        echo "  ✓ 已删除前端相关文件"
    else
        echo "  - 跳过删除前端文件"
    fi
else
    echo "  - 未发现前端相关文件"
fi
if [ "$MODULE_NAME" = "screen" ]; then
    echo "  提示: 大屏编辑器为独立应用 apps/forge-screen，如不再使用请手动删除"
fi

# 7. 删除模块目录
echo "[7/7] 删除模块目录..."
echo "  模块目录: $MODULE_DIR"
if confirm "  确认删除模块目录? (y/N): "; then
    rm -rf "$MODULE_DIR"
    echo "  ✓ 已删除模块目录"
else
    echo "  - 跳过删除模块目录"
fi

echo ""
echo "=== 完成 ==="
echo ""
echo "后续步骤建议:"
echo "1. 运行 'cd apps/forge-server && mvn clean compile' 验证编译"
echo "2. 检查其他模块代码中对本模块的 Java 引用（如有）"
echo "3. 清理数据库中本模块的表和菜单数据（sys_menu，如有）"
echo "4. 运行 'git status' 查看变更"
