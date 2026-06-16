#!/bin/bash
#
# 删除 Maven 模块脚本
# 用法: ./scripts/remove-module.sh <模块名称>
# 示例: ./scripts/remove-module.sh workflow
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SERVER_DIR="$PROJECT_ROOT/apps/forge-server"

# 验证参数
if [ -z "$1" ]; then
    echo "用法: $0 <模块名称>"
    echo "示例: $0 workflow"
    echo "示例: $0 system"
    exit 1
fi

MODULE_NAME="$1"
MODULE_DIR="$SERVER_DIR/forge-module-$MODULE_NAME"

# 检查模块是否存在
if [ ! -d "$MODULE_DIR" ]; then
    echo "错误: 模块目录不存在: $MODULE_DIR"
    exit 1
fi

echo "=== 删除模块: $MODULE_NAME ==="
echo ""

# 1. 从根 pom.xml 移除模块引用
echo "[1/6] 从根 pom.xml 移除模块引用..."
ROOT_POM="$SERVER_DIR/pom.xml"
if grep -q "<module>forge-module-$MODULE_NAME</module>" "$ROOT_POM"; then
    sed -i.bak "/<module>forge-module-$MODULE_NAME</module>/d" "$ROOT_POM"
    rm -f "$ROOT_POM.bak"
    echo "  ✓ 已从根 pom.xml 移除 forge-module-$MODULE_NAME"
else
    echo "  - 根 pom.xml 中未找到该模块引用"
fi

# 2. 从 forge-dependencies 移除依赖声明
echo "[2/6] 从 forge-dependencies 移除依赖声明..."
DEPS_POM="$SERVER_DIR/forge-dependencies/pom.xml"
if [ -f "$DEPS_POM" ]; then
    # 移除 api 模块依赖
    if grep -q "forge-module-$MODULE_NAME-api" "$DEPS_POM"; then
        # 删除整个 dependency 块
        sed -i.bak "/<artifactId>forge-module-$MODULE_NAME-api<\/artifactId>/,/<\/dependency>/d" "$DEPS_POM"
        rm -f "$DEPS_POM.bak"
        echo "  ✓ 已移除 forge-module-$MODULE_NAME-api 依赖声明"
    fi
    # 移除 biz 模块依赖
    if grep -q "forge-module-$MODULE_NAME-biz" "$DEPS_POM"; then
        sed -i.bak "/<artifactId>forge-module-$MODULE_NAME-biz<\/artifactId>/,/<\/dependency>/d" "$DEPS_POM"
        rm -f "$DEPS_POM.bak"
        echo "  ✓ 已移除 forge-module-$MODULE_NAME-biz 依赖声明"
    fi
else
    echo "  - forge-dependencies/pom.xml 不存在"
fi

# 3. 从 forge-server 移除依赖引用
echo "[3/6] 从 forge-server 移除依赖引用..."
SERVER_POM="$SERVER_DIR/forge-server/pom.xml"
if [ -f "$SERVER_POM" ]; then
    # 移除 biz 模块依赖
    if grep -q "forge-module-$MODULE_NAME-biz" "$SERVER_POM"; then
        sed -i.bak "/<artifactId>forge-module-$MODULE_NAME-biz<\/artifactId>/,/<\/dependency>/d" "$SERVER_POM"
        rm -f "$SERVER_POM.bak"
        echo "  ✓ 已从 forge-server 移除 forge-module-$MODULE_NAME-biz 依赖"
    fi
else
    echo "  - forge-server/pom.xml 不存在"
fi

# 4. 检查其他模块的依赖并移除
echo "[4/6] 检查其他模块的依赖..."
for other_pom in $(find "$SERVER_DIR" -name "pom.xml" -not -path "$MODULE_DIR/*"); do
    if grep -q "forge-module-$MODULE_NAME" "$other_pom"; then
        echo "  发现依赖: $other_pom"
        sed -i.bak "/forge-module-$MODULE_NAME/d" "$other_pom"
        rm -f "$other_pom.bak"
        echo "  ✓ 已移除 $other_pom 中的依赖"
    fi
done

# 5. 删除数据库迁移脚本（可选）
echo "[5/6] 检查数据库迁移脚本..."
MIGRATION_DIR="$SERVER_DIR/forge-server/src/main/resources/db/migration"
if [ -d "$MIGRATION_DIR" ]; then
    # 查找包含模块名称的迁移脚本
    migration_files=$(find "$MIGRATION_DIR" -name "*.sql" -exec grep -l "$MODULE_NAME\|wf" {} \; 2>/dev/null)
    if [ -n "$migration_files" ]; then
        echo "  发现相关迁移脚本:"
        echo "$migration_files"
        read -p "  是否删除这些迁移脚本? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "$migration_files" | xargs rm -f
            echo "  ✓ 已删除迁移脚本"
        else
            echo "  - 跳过删除迁移脚本"
        fi
    else
        echo "  - 未发现相关迁移脚本"
    fi
fi

# 6. 删除模块目录
echo "[6/6] 删除模块目录..."
echo "  模块目录: $MODULE_DIR"
read -p "  确认删除模块目录? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
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
echo "2. 检查并删除前端相关页面（如有）"
echo "3. 检查并删除数据库中相关表（如有）"
echo "4. 运行 'git status' 查看变更"