#!/usr/bin/env node

/**
 * 创建新业务模块脚本
 *
 * 使用方法: pnpm run create-module <模块名> "<模块描述>"
 * 示例: pnpm run create-module wms "仓储管理模块"
 */

const fs = require('fs')
const path = require('path')

const colors = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  cyan: '\x1b[36m'
}

function log(message, color = 'reset') {
  console.log(`${colors[color]}${message}${colors.reset}`)
}

// 解析命令行参数
const args = process.argv.slice(2)
if (args.length < 2) {
  log('使用方法: pnpm run create-module <模块名> "<模块描述>"', 'yellow')
  log('示例: pnpm run create-module wms "仓储管理模块"', 'cyan')
  process.exit(1)
}

const moduleName = args[0]
const description = args[1]

// 校验模块名格式 (kebab-case)
if (!/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/.test(moduleName)) {
  log('错误: 模块名必须为 kebab-case 格式（如 wms、order-management）', 'red')
  process.exit(1)
}

const rootDir = path.resolve(__dirname, '..')
const serverDir = path.join(rootDir, 'apps', 'forge-server')
const moduleDir = path.join(serverDir, `forge-module-${moduleName}`)

// 检查模块是否已存在
if (fs.existsSync(moduleDir)) {
  log(`错误: 模块 forge-module-${moduleName} 已存在`, 'red')
  process.exit(1)
}

log('\n========================================', 'cyan')
log('  创建新业务模块', 'cyan')
log('========================================\n', 'cyan')
log(`  模块名: forge-module-${moduleName}`)
log(`  描述: ${description}\n`)

// ========================================
// 1. 创建目录结构
// ========================================
log('1. 创建目录结构...', 'yellow')

const dirs = [
  // api 模块
  `forge-module-${moduleName}-api/src/main/java/com/forge/modules/${moduleName}/entity`,
  `forge-module-${moduleName}-api/src/main/java/com/forge/modules/${moduleName}/dto`,
  // biz 模块
  `forge-module-${moduleName}-biz/src/main/java/com/forge/modules/${moduleName}/controller/admin`,
  `forge-module-${moduleName}-biz/src/main/java/com/forge/modules/${moduleName}/controller/app`,
  `forge-module-${moduleName}-biz/src/main/java/com/forge/modules/${moduleName}/mapper`,
  `forge-module-${moduleName}-biz/src/main/java/com/forge/modules/${moduleName}/service/impl`,
  `forge-module-${moduleName}-biz/src/main/resources/mapper/${moduleName}`,
]

dirs.forEach(dir => {
  const fullPath = path.join(moduleDir, dir)
  fs.mkdirSync(fullPath, { recursive: true })
  log(`  ✓ ${dir}`, 'green')
})

// ========================================
// 2. 生成聚合 POM
// ========================================
log('\n2. 生成 POM 文件...', 'yellow')

const moduleArtifactId = `forge-module-${moduleName}`
const apiArtifactId = `forge-module-${moduleName}-api`
const bizArtifactId = `forge-module-${moduleName}-biz`

// 聚合 POM
const aggregatorPom = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>${moduleArtifactId}</artifactId>
    <packaging>pom</packaging>
    <name>${moduleArtifactId}</name>
    <description>${description}</description>

    <modules>
        <module>${apiArtifactId}</module>
        <module>${bizArtifactId}</module>
    </modules>
</project>
`
fs.writeFileSync(path.join(moduleDir, 'pom.xml'), aggregatorPom)
log(`  ✓ 聚合 POM`, 'green')

// API POM
const apiPom = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>${moduleArtifactId}</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>${apiArtifactId}</artifactId>
    <name>${apiArtifactId}</name>
    <description>${description} - 接口、实体、DTO</description>

    <dependencies>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
</project>
`
fs.writeFileSync(path.join(moduleDir, `${apiArtifactId}/pom.xml`), apiPom)
log(`  ✓ API POM`, 'green')

// BIZ POM
const bizPom = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>${moduleArtifactId}</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>${bizArtifactId}</artifactId>
    <name>${bizArtifactId}</name>
    <description>${description} - 业务实现</description>

    <dependencies>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>${apiArtifactId}</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-module-system-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
`
fs.writeFileSync(path.join(moduleDir, `${bizArtifactId}/pom.xml`), bizPom)
log(`  ✓ BIZ POM`, 'green')

// ========================================
// 3. 修改根 POM（添加 module 声明）
// ========================================
log('\n3. 修改根 POM...', 'yellow')

const rootPomPath = path.join(serverDir, 'pom.xml')
let rootPom = fs.readFileSync(rootPomPath, 'utf8')

const newModuleEntry = `        <module>${moduleArtifactId}</module>`
const lastModuleEntry = '        <module>forge-server</module>'
rootPom = rootPom.replace(lastModuleEntry, `${newModuleEntry}\n${lastModuleEntry}`)
fs.writeFileSync(rootPomPath, rootPom)
log(`  ✓ 添加 <module>${moduleArtifactId}</module>`, 'green')

// ========================================
// 4. 修改启动模块 POM（添加依赖）
// ========================================
log('\n4. 修改启动模块 POM...', 'yellow')

const serverPomPath = path.join(serverDir, 'forge-server/pom.xml')
let serverPom = fs.readFileSync(serverPomPath, 'utf8')

const newDep = `        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>${bizArtifactId}</artifactId>
        </dependency>
`
const lastBizDep = '        <dependency>\n            <groupId>com.forge</groupId>\n            <artifactId>forge-module-workflow-biz</artifactId>\n        </dependency>'
serverPom = serverPom.replace(lastBizDep, `${lastBizDep}\n${newDep}`)
fs.writeFileSync(serverPomPath, serverPom)
log(`  ✓ 添加 ${bizArtifactId} 依赖`, 'green')

// ========================================
// 5. 更新 init-project.js 替换规则
// ========================================
const initScriptPath = path.join(rootDir, 'scripts/init-project.js')

// init 生成的新项目不包含脚手架自身（init-project.js 已被排除），无需更新替换规则
if (!fs.existsSync(initScriptPath)) {
  log('\n5. 跳过更新 init-project.js 替换规则（本项目非模板项目）', 'cyan')
} else {
log('\n5. 更新 init-project.js 替换规则...', 'yellow')

let initScript = fs.readFileSync(initScriptPath, 'utf8')

// 在 Maven 子模块替换规则中添加新模块
const moduleReplaceComment = "    // Maven 子模块（长名优先）"
const newBizRule = `    { from: '${moduleArtifactId}-biz', to: \`\${config.nameKebab}-module-${moduleName}-biz\` },`
const newApiRule = `    { from: '${moduleArtifactId}-api', to: \`\${config.nameKebab}-module-${moduleName}-api\` },`
const newModuleRule = `    { from: '${moduleArtifactId}', to: \`\${config.nameKebab}-module-${moduleName}\` },`
const newRules = `${newBizRule}\n${newApiRule}\n${newModuleRule}\n${moduleReplaceComment}`

if (initScript.includes(moduleReplaceComment)) {
  initScript = initScript.replace(moduleReplaceComment, newRules)
  fs.writeFileSync(initScriptPath, initScript)
  log(`  ✓ 添加替换规则: ${moduleArtifactId}`, 'green')
}

// 在 init-project.js 子模块目录重命名中添加新条目
const submoduleRenamesMarker = "      // forge-framework 子目录（深层优先）"
const newBizDirRename = `      { from: '${moduleArtifactId}/${apiArtifactId}', to: \`${moduleArtifactId}/\${config.nameKebab}-module-${moduleName}-api\` },\n      { from: '${moduleArtifactId}/${bizArtifactId}', to: \`${moduleArtifactId}/\${config.nameKebab}-module-${moduleName}-biz\` },`
const newModuleDirRename = `      { from: '${moduleArtifactId}', to: \`\${config.nameKebab}-module-${moduleName}\` },`
const newDirRenames = `      // ${description}\n${newBizDirRename}\n      // 中层目录\n${submoduleRenamesMarker}`

if (initScript.includes(submoduleRenamesMarker)) {
  initScript = initScript.replace(submoduleRenamesMarker, newDirRenames)
  // 需要在中层目录部分也加入新模块的中层目录重命名
  // 找到 "      { from: 'forge-dependencies'" 前面插入
  const midLevelMarker = "      { from: 'forge-dependencies'"
  initScript = initScript.replace(midLevelMarker, `${newModuleDirRename}\n${midLevelMarker}`)
  fs.writeFileSync(initScriptPath, initScript)
  log(`  ✓ 添加目录重命名规则: ${moduleArtifactId}`, 'green')
}
}

log('\n========================================', 'cyan')
log('  模块创建完成！', 'green')
log('========================================\n', 'cyan')

log('目录结构:', 'yellow')
log(`  apps/forge-server/${moduleArtifactId}/`)
log(`  ├── pom.xml`)
log(`  ├── ${apiArtifactId}/`)
log(`  │   ├── pom.xml`)
log(`  │   └── src/main/java/com/forge/modules/${moduleName}/`)
log(`  │       ├── entity/`)
log(`  │       └── dto/`)
log(`  └── ${bizArtifactId}/`)
log(`      ├── pom.xml`)
log(`      └── src/main/java/com/forge/modules/${moduleName}/`)
log(`          ├── controller/admin/  (后台管理端点)`)
  log(`          ├── controller/app/   (移动端端点)`)
log(`          ├── mapper/`)
log(`          └── service/impl/`)

log('\n后续步骤:', 'yellow')
log(`  1. 在 entity/ 中创建实体类`)
log(`  2. 在 dto/ 中创建 Request/Response/QueryRequest`)
log(`  3. 在 mapper/ 中创建 Mapper 接口`)
log(`  4. 在 service/impl/ 中创建 Service 接口和实现`)
log(`  5. 在 controller/ 中创建 Controller`)
log(`  6. 运行: cd apps/forge-server && mvn clean compile 验证编译`)
log('')
