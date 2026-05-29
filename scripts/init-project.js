#!/usr/bin/env node

/**
 * 项目初始化脚本
 * 用于基于 forge-admin 模板创建新项目
 *
 * 使用方法: pnpm run init <项目名称> "<项目描述>" <包名>
 * 示例: pnpm run init my-admin "我的管理系统" com.mycompany
 */

const fs = require('fs')
const path = require('path')

// 颜色输出
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
function parseArgs() {
  const args = process.argv.slice(2)

  if (args.length < 3) {
    log('使用方法: pnpm run init <项目名称> "<项目描述>" <包名>', 'yellow')
    log('示例: pnpm run init my-admin "我的管理系统" com.mycompany', 'cyan')
    process.exit(1)
  }

  const projectName = args[0]
  const description = args[1]
  const basePackage = args[2]

  const nameKebab = projectName
    .replace(/([a-z])([A-Z])/g, '$1-$2')
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '-')

  const nameSnake = nameKebab.replace(/-/g, '_')

  return {
    projectName,
    description,
    basePackage,
    nameKebab,
    nameSnake
  }
}

// 验证包名
function validatePackageName(packageName) {
  const regex = /^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$/
  if (!regex.test(packageName)) {
    log('错误: 包名格式不正确，应为: com.example 或 com.company.project', 'red')
    process.exit(1)
  }
}

// 递归获取目录下所有文件
function getAllFiles(dirPath, arrayOfFiles = []) {
  const files = fs.readdirSync(dirPath)

  files.forEach(file => {
    const fullPath = path.join(dirPath, file)

    // 跳过特定目录
    if ([
      'node_modules', 'target', 'dist', '.git', '.idea',
      'logs', 'uploads', '.claude'
    ].includes(file)) {
      return
    }

    if (fs.statSync(fullPath).isDirectory()) {
      getAllFiles(fullPath, arrayOfFiles)
    } else {
      arrayOfFiles.push(fullPath)
    }
  })

  return arrayOfFiles
}

// 替换文件内容
function replaceInFile(filePath, replacements) {
  try {
    let content = fs.readFileSync(filePath, 'utf8')
    let modified = false

    replacements.forEach(({ from, to }) => {
      if (content.includes(from)) {
        content = content.split(from).join(to)
        modified = true
      }
    })

    if (modified) {
      fs.writeFileSync(filePath, content)
      return true
    }
    return false
  } catch (error) {
    return false
  }
}

// 重命名 Java 包目录
function renamePackageDir(oldPackage, newPackage, srcDir) {
  const oldPath = path.join(srcDir, oldPackage.replace(/\./g, '/'))
  const newPath = path.join(srcDir, newPackage.replace(/\./g, '/'))

  if (!fs.existsSync(oldPath)) {
    return false
  }

  // 确保目标父目录存在
  const newParentDir = path.dirname(newPath)
  if (!fs.existsSync(newParentDir)) {
    fs.mkdirSync(newParentDir, { recursive: true })
  }

  // 移动目录
  try {
    fs.cpSync(oldPath, newPath, { recursive: true })
    fs.rmSync(oldPath, { recursive: true })
    log(`  ✓ 重命名包目录: ${oldPackage} -> ${newPackage} (${path.relative(process.cwd(), srcDir)})`, 'green')
    return true
  } catch (error) {
    log(`  ✗ 重命名包目录失败: ${error.message}`, 'red')
    return false
  }
}

// 清理空目录
function cleanEmptyDirs(dirPath) {
  if (!fs.existsSync(dirPath)) return

  const files = fs.readdirSync(dirPath)

  if (files.length === 0) {
    fs.rmdirSync(dirPath)
    cleanEmptyDirs(path.dirname(dirPath))
  } else {
    files.forEach(file => {
      const fullPath = path.join(dirPath, file)
      if (fs.statSync(fullPath).isDirectory()) {
        cleanEmptyDirs(fullPath)
      }
    })
  }
}

// 查找所有 src/main/java 目录
function findJavaSourceRoots(baseDir) {
  const results = []
  const skipDirs = ['node_modules', 'target', 'dist', '.git', '.idea', 'logs', 'uploads', '.claude']

  function walk(dir) {
    const name = path.basename(dir)
    if (skipDirs.includes(name)) return

    if (dir.endsWith(path.join('src', 'main', 'java'))) {
      results.push(dir)
      return
    }

    try {
      const entries = fs.readdirSync(dir, { withFileTypes: true })
      for (const entry of entries) {
        if (entry.isDirectory()) {
          walk(path.join(dir, entry.name))
        }
      }
    } catch (e) {
      // ignore
    }
  }

  walk(baseDir)
  return results
}

// 主函数
function main() {
  log('\n========================================', 'cyan')
  log('  forge-admin 项目初始化脚本', 'cyan')
  log('========================================\n', 'cyan')

  const config = parseArgs()
  validatePackageName(config.basePackage)

  log('配置信息:', 'yellow')
  log(`  项目名称: ${config.projectName}`)
  log(`  项目描述: ${config.description}`)
  log(`  包名: ${config.basePackage}`)
  log(`  标识符 (kebab): ${config.nameKebab}`)
  log(`  标识符 (snake): ${config.nameSnake}\n`)

  const rootDir = path.resolve(__dirname, '..')
  process.chdir(rootDir)

  const namePascal = config.nameKebab
    .split('-')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')

  // 定义替换规则（顺序重要：先匹配长的字符串，避免部分匹配）
  const replacements = [
    // 后端替换
    { from: 'ForgeAdminApplication', to: `${namePascal}Application` },
    { from: 'com.forge.admin', to: config.basePackage },
    { from: 'com.forge', to: config.basePackage },
    { from: 'forge_admin-page-config', to: `${config.nameSnake}-page-config` },
    { from: 'forge-admin', to: config.nameKebab },
    { from: 'forge_admin', to: config.nameSnake },
    { from: '聚能后台管理系统', to: config.description },
    { from: 'forge-server', to: `${config.nameKebab}-server` },
    { from: 'forge-web', to: `${config.nameKebab}-web` },
  ]

  // 需要处理的文件扩展名
  const targetExtensions = [
    '.java', '.xml', '.yml', '.yaml', '.properties',
    '.vue', '.ts', '.js', '.json', '.html', '.env', '.sql', '.md',
    '.imports'
  ]

  log('1. 替换文件内容...', 'yellow')
  const files = getAllFiles('.')
  let replacedCount = 0

  files.forEach(file => {
    const ext = path.extname(file)
    if (targetExtensions.includes(ext) || file.includes('.env')) {
      if (replaceInFile(file, replacements)) {
        replacedCount++
        log(`  ✓ ${file}`, 'green')
      }
    }
  })

  log(`\n  共替换 ${replacedCount} 个文件\n`)

  // 重命名 Java 包目录（自动扫描所有模块）
  log('2. 重命名 Java 包目录...', 'yellow')
  const serverDir = path.join(rootDir, 'apps/forge-server')
  const javaSourceRoots = findJavaSourceRoots(serverDir)

  log(`  发现 ${javaSourceRoots.length} 个 Java 源码根目录`, 'cyan')

  // 先尝试 com.forge.admin（兼容旧模板），再尝试 com.forge（当前包名）
  javaSourceRoots.forEach(javaDir => {
    if (!renamePackageDir('com.forge', config.basePackage, javaDir)) {
      renamePackageDir('com.forge.admin', config.basePackage, javaDir)
    }
  })

  // 清理空目录
  javaSourceRoots.forEach(javaDir => {
    const oldPackagePath = path.join(javaDir, 'com/forge/admin')
    if (fs.existsSync(path.dirname(oldPackagePath))) {
      cleanEmptyDirs(path.dirname(oldPackagePath))
    }
    const oldPackagePath2 = path.join(javaDir, 'com/forge')
    if (fs.existsSync(path.dirname(oldPackagePath2))) {
      cleanEmptyDirs(path.dirname(oldPackagePath2))
    }
  })

  // 重命名启动类文件
  log('\n3. 重命名启动类...', 'yellow')
  const newPackagePath = config.basePackage.replace(/\./g, '/')
  javaSourceRoots.forEach(javaDir => {
    const oldAppFile = path.join(javaDir, newPackagePath, 'ForgeAdminApplication.java')
    if (fs.existsSync(oldAppFile)) {
      const newAppFile = path.join(javaDir, newPackagePath, `${namePascal}Application.java`)
      fs.renameSync(oldAppFile, newAppFile)
      log(`  ✓ ForgeAdminApplication.java -> ${namePascal}Application.java`, 'green')
    }
  })

  // 重命名应用目录
  log('\n4. 重命名应用目录...', 'yellow')
  const dirRenames = [
    { from: 'forge-server', to: `${config.nameKebab}-server` },
    { from: 'forge-web', to: `${config.nameKebab}-web` },
  ]
  dirRenames.forEach(({ from, to }) => {
    const oldDir = path.join(rootDir, 'apps', from)
    const newDir = path.join(rootDir, 'apps', to)
    if (fs.existsSync(oldDir) && from !== to) {
      fs.renameSync(oldDir, newDir)
      log(`  ✓ apps/${from} -> apps/${to}`, 'green')
    }
  })

  // 更新数据库初始化脚本
  log('\n5. 更新数据库脚本...', 'yellow')
  const sqlFile = path.join(rootDir, 'sql/init.sql')
  if (fs.existsSync(sqlFile)) {
    replaceInFile(sqlFile, [
      { from: 'forge_admin', to: config.nameSnake }
    ])
    log('  ✓ 更新 sql/init.sql', 'green')
  }

  log('\n========================================', 'cyan')
  log('  初始化完成！', 'green')
  log('========================================\n', 'cyan')

  log('后续步骤:', 'yellow')
  log('  1. 创建数据库: mysql -u root -p < sql/init.sql')
  log('  2. 更新 .env 文件中的配置')
  log(`  3. 启动后端: cd apps/${config.nameKebab}-server && mvn spring-boot:run`)
  log(`  4. 启动前端: cd apps/${config.nameKebab}-web && pnpm dev`)
  log('')
}

main()
