<template>
  <div class="dashboard">
    <!-- 轮播图区域 -->
    <el-card shadow="never" class="banner-card">
      <el-carousel height="200px" :interval="5000" arrow="hover" indicator-position="">
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <div class="banner-item" :style="{ background: banner.background }">
            <div class="banner-content">
              <h2>{{ banner.title }}</h2>
              <p>{{ banner.description }}</p>
            </div>
            <div class="banner-image">
              <el-icon :size="80" :color="banner.iconColor"><component :is="banner.icon" /></el-icon>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <div class="stat-card" @click="$router.push('/system/user')">
          <div class="stat-icon user">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.userCount }}</div>
            <div class="stat-label">用户总数</div>
            <div class="stat-trend" :class="{ up: stats.userTrend > 0, down: stats.userTrend < 0 }">
              <el-icon><component :is="stats.userTrend > 0 ? 'Top' : 'Bottom'" /></el-icon>
              {{ Math.abs(stats.userTrend) || 0 }}%
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <div class="stat-card" @click="$router.push('/system/role')">
          <div class="stat-icon role">
            <el-icon><UserFilled /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.roleCount }}</div>
            <div class="stat-label">角色总数</div>
            <div class="stat-trend-placeholder"></div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <div class="stat-card" @click="$router.push('/system/menu')">
          <div class="stat-icon menu">
            <el-icon><Menu /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.menuCount }}</div>
            <div class="stat-label">菜单总数</div>
            <div class="stat-trend-placeholder"></div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <div class="stat-card" @click="$router.push('/system/operation-log')">
          <div class="stat-icon log">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.logCount }}</div>
            <div class="stat-label">操作日志</div>
            <div class="stat-trend" :class="{ up: stats.logTrend > 0, down: stats.logTrend < 0 }">
              <el-icon><component :is="stats.logTrend > 0 ? 'Top' : 'Bottom'" /></el-icon>
              {{ Math.abs(stats.logTrend) || 0 }}%
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 主要内容区 -->
    <el-row :gutter="16" class="content-row">
      <!-- 左侧列 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="16" :xl="16">
        <!-- 常用功能收藏 -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Star /></el-icon>
                常用功能
              </span>
              <el-button type="primary" link @click="editFavorites = true">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
          </template>
          <div class="favorites-grid">
            <div
              v-for="item in favoriteItems"
              :key="item.id"
              class="favorite-item"
              @click="$router.push(item.path)"
            >
              <div class="favorite-icon" :style="{ background: item.color }">
                <el-icon><component :is="item.icon" /></el-icon>
              </div>
              <span class="favorite-label">{{ item.label }}</span>
            </div>
          </div>
        </el-card>

        <!-- 数据对比 -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><DataAnalysis /></el-icon>
                数据对比
              </span>
              <el-radio-group v-model="comparePeriod" size="small">
                <el-radio-button value="week">本周</el-radio-button>
                <el-radio-button value="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div class="compare-cards">
            <div class="compare-item">
              <div class="compare-label">用户增长</div>
              <div class="compare-value">
                <span class="current">+{{ compareData.userGrowth }}</span>
                <span class="compare-trend up">
                  <el-icon><Top /></el-icon>
                  {{ compareData.userGrowthRate }}%
                </span>
              </div>
              <div class="compare-sub">较上{{ comparePeriod === 'week' ? '周' : '月' }}增长</div>
            </div>
            <div class="compare-item">
              <div class="compare-label">访问量</div>
              <div class="compare-value">
                <span class="current">{{ compareData.visitCount }}</span>
                <span class="compare-trend up">
                  <el-icon><Top /></el-icon>
                  {{ compareData.visitRate }}%
                </span>
              </div>
              <div class="compare-sub">较上{{ comparePeriod === 'week' ? '周' : '月' }}变化</div>
            </div>
            <div class="compare-item">
              <div class="compare-label">操作次数</div>
              <div class="compare-value">
                <span class="current">{{ compareData.operationCount }}</span>
                <span class="compare-trend" :class="{ down: compareData.operationRate < 0 }">
                  <el-icon><component :is="compareData.operationRate >= 0 ? 'Top' : 'Bottom'" /></el-icon>
                  {{ Math.abs(compareData.operationRate) }}%
                </span>
              </div>
              <div class="compare-sub">较上{{ comparePeriod === 'week' ? '周' : '月' }}变化</div>
            </div>
            <div class="compare-item">
              <div class="compare-label">活跃用户</div>
              <div class="compare-value">
                <span class="current">{{ compareData.activeUsers }}</span>
                <span class="compare-trend up">
                  <el-icon><Top /></el-icon>
                  {{ compareData.activeRate }}%
                </span>
              </div>
              <div class="compare-sub">较上{{ comparePeriod === 'week' ? '周' : '月' }}变化</div>
            </div>
          </div>
        </el-card>

        <!-- 数据概览图表 -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <span class="card-title">
              <el-icon><TrendCharts /></el-icon>
              访问趋势（近7天）
            </span>
          </template>
          <div class="chart-container">
            <div class="bar-chart">
              <div v-for="(item, index) in visitData" :key="index" class="bar-item">
                <div class="bar-wrapper">
                  <div class="bar" :style="{ height: getBarHeight(item.value) + '%' }"></div>
                  <div class="bar-tooltip">{{ item.value }}</div>
                </div>
                <span class="bar-label">{{ item.label }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧列 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="8" :xl="8" class="right-column">
        <!-- 待办事项 -->
        <el-card shadow="never" class="content-card fixed-height-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><List /></el-icon>
                待办事项
              </span>
              <el-button type="primary" link @click="addTodo">
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
          </template>
          <div class="todo-list">
            <div v-for="todo in todos" :key="todo.id" class="todo-item" :class="{ completed: todo.completed }">
              <el-checkbox v-model="todo.completed" @change="toggleTodo(todo)">
                <span :class="{ 'todo-text': true, completed: todo.completed }">{{ todo.text }}</span>
              </el-checkbox>
              <el-button link type="danger" @click="deleteTodo(todo.id)">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
            <el-empty v-if="todos.length === 0" description="暂无待办事项" :image-size="50" />
          </div>
        </el-card>

        <!-- 最新公告 -->
        <el-card shadow="never" class="content-card fixed-height-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Bell /></el-icon>
                最新公告
              </span>
              <el-button type="primary" link @click="$router.push('/system/notice')">更多</el-button>
            </div>
          </template>
          <div v-if="notices.length > 0" class="notice-list">
            <div v-for="notice in notices" :key="notice.id" class="notice-item" @click="showNotice(notice)">
              <el-tag :type="notice.noticeType === 1 ? 'warning' : 'success'" size="small">
                {{ notice.noticeType === 1 ? '通知' : '公告' }}
              </el-tag>
              <span class="notice-title">{{ notice.noticeTitle }}</span>
              <span class="notice-time">{{ formatDate(notice.createTime) }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无公告" :image-size="50" />
        </el-card>

        <!-- 系统信息 -->
        <el-card shadow="never" class="content-card auto-height-card">
          <template #header>
            <span class="card-title">
              <el-icon><Monitor /></el-icon>
              系统信息
            </span>
          </template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="系统名称">forge-admin</el-descriptions-item>
            <el-descriptions-item label="系统版本">1.0.0</el-descriptions-item>
            <el-descriptions-item label="运行时间">{{ systemInfo.uptime }}</el-descriptions-item>
            <el-descriptions-item label="内存使用">{{ systemInfo.memory }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑收藏对话框 -->
    <el-dialog v-model="editFavorites" title="编辑常用功能" width="600px">
      <div class="edit-favorites">
        <div class="available-functions">
          <div
            v-for="item in allFunctions"
            :key="item.id"
            class="function-item"
            :class="{ selected: isFavorite(item.id) }"
            @click="toggleFavorite(item)"
          >
            <el-icon class="item-icon"><component :is="item.icon" /></el-icon>
            <span class="item-label">{{ item.label }}</span>
            <el-icon v-if="isFavorite(item.id)" class="check-icon" color="#67c23a"><Check /></el-icon>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="editFavorites = false">取消</el-button>
        <el-button type="primary" @click="saveFavorites">确定</el-button>
      </template>
    </el-dialog>

    <!-- 公告详情对话框 -->
    <el-dialog v-model="noticeVisible" title="公告详情" width="600px">
      <el-descriptions :column="1" border v-if="currentNotice">
        <el-descriptions-item label="公告标题">{{ currentNotice.noticeTitle }}</el-descriptions-item>
        <el-descriptions-item label="公告类型">
          <el-tag :type="currentNotice.noticeType === 1 ? 'warning' : 'success'">
            {{ currentNotice.noticeType === 1 ? '通知' : '公告' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ formatDate(currentNotice.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="公告内容">
          <div class="notice-content">{{ currentNotice.noticeContent }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useRoute } from 'vue-router'
import { getDashboardStats, getLatestNotices, type DashboardStats, type Notice } from '@/api/system'

const route = useRoute()
const userStore = useUserStore()

// 轮播图数据
const banners = ref([
  {
    id: 1,
    title: '欢迎使用 forge-admin',
    description: '现代化的一站式后台管理系统',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    icon: 'House',
    iconColor: '#fff'
  },
  {
    id: 2,
    title: '高效管理，轻松办公',
    description: '强大的权限控制与数据管理',
    background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    icon: 'Trophy',
    iconColor: '#fff'
  },
  {
    id: 3,
    title: '数据驱动，智能决策',
    description: '实时监控与数据分析平台',
    background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    icon: 'TrendCharts',
    iconColor: '#fff'
  }
])

// 统计数据
const stats = ref<DashboardStats & { userTrend?: number; logTrend?: number }>({
  userCount: 0,
  roleCount: 0,
  menuCount: 0,
  logCount: 0,
  deptCount: 0,
  positionCount: 0,
  dictCount: 0,
  configCount: 0,
  userTrend: 12.5,
  logTrend: -5.2
})

// 公告数据
const notices = ref<Notice[]>([])
const noticeVisible = ref(false)
const currentNotice = ref<Notice | null>(null)

// 访问趋势数据
const visitData = ref([
  { label: '周一', value: 120 },
  { label: '周二', value: 180 },
  { label: '周三', value: 150 },
  { label: '周四', value: 200 },
  { label: '周五', value: 280 },
  { label: '周六', value: 90 },
  { label: '周日', value: 60 }
])

// 系统信息
const systemInfo = ref({
  uptime: '12天 5小时',
  memory: '256 MB / 512 MB'
})

// 数据对比周期
const comparePeriod = ref<'week' | 'month'>('week')

// 数据对比数据
const compareData = computed(() => {
  if (comparePeriod.value === 'week') {
    return {
      userGrowth: 15,
      userGrowthRate: 23,
      visitCount: 1256,
      visitRate: 18,
      operationCount: 3420,
      operationRate: -5,
      activeUsers: 89,
      activeRate: 12
    }
  } else {
    return {
      userGrowth: 58,
      userGrowthRate: 35,
      visitCount: 4890,
      visitRate: 22,
      operationCount: 12500,
      operationRate: 15,
      activeUsers: 156,
      activeRate: 28
    }
  }
})

// 所有可收藏的功能
const allFunctions = ref([
  { id: 'user', label: '用户管理', icon: 'User', path: '/system/user', color: '#e8f4ff' },
  { id: 'role', label: '角色管理', icon: 'UserFilled', path: '/system/role', color: '#f0f9eb' },
  { id: 'menu', label: '菜单管理', icon: 'Menu', path: '/system/menu', color: '#fdf6ec' },
  { id: 'dept', label: '部门管理', icon: 'OfficeBuilding', path: '/system/dept', color: '#f4f4f5' },
  { id: 'position', label: '岗位管理', icon: 'Briefcase', path: '/system/position', color: '#fef0f0' },
  { id: 'dict', label: '字典管理', icon: 'Notebook', path: '/system/dict-type', color: '#fdf6ec' },
  { id: 'config', label: '系统配置', icon: 'Tools', path: '/system/config', color: '#e8f4ff' },
  { id: 'notice', label: '通知公告', icon: 'Bell', path: '/system/notice', color: '#f4f4f5' },
  { id: 'oplog', label: '操作日志', icon: 'Document', path: '/system/operation-log', color: '#fef0f0' },
  { id: 'loginlog', label: '登录日志', icon: 'Monitor', path: '/system/login-log', color: '#f0f9eb' },
  { id: 'online', label: '在线用户', icon: 'User', path: '/system/online-user', color: '#fdf6ec' },
  { id: 'job', label: '定时任务', icon: 'Timer', path: '/system/job', color: '#e8f4ff' }
])

// 收藏的功能ID列表
const favoriteIds = ref<string[]>(['user', 'role', 'menu', 'config'])

// 编辑收藏状态
const editFavorites = ref(false)

// 收藏的功能项
const favoriteItems = computed(() => {
  return allFunctions.value
    .filter(item => favoriteIds.value.includes(item.id))
})

// 判断是否已收藏
const isFavorite = (id: string) => {
  return favoriteIds.value.includes(id)
}

// 切换收藏状态
const toggleFavorite = (item: any) => {
  const index = favoriteIds.value.indexOf(item.id)
  if (index > -1) {
    favoriteIds.value.splice(index, 1)
  } else {
    favoriteIds.value.push(item.id)
  }
}

// 保存收藏
const saveFavorites = () => {
  localStorage.setItem('dashboard-favorites', JSON.stringify(favoriteIds.value))
  editFavorites.value = false
}

// 待办事项
const todos = ref([
  { id: 1, text: '审查新用户注册申请', completed: false },
  { id: 2, text: '更新系统配置参数', completed: true },
  { id: 3, text: '备份数据库', completed: false }
])

const toggleTodo = (todo: any) => {
  localStorage.setItem('dashboard-todos', JSON.stringify(todos.value))
}

const deleteTodo = (id: number) => {
  todos.value = todos.value.filter(t => t.id !== id)
  localStorage.setItem('dashboard-todos', JSON.stringify(todos.value))
}

const addTodo = () => {
  const text = prompt('请输入待办事项：')
  if (text) {
    todos.value.push({
      id: Date.now(),
      text,
      completed: false
    })
    localStorage.setItem('dashboard-todos', JSON.stringify(todos.value))
  }
}

// 图表相关
const getBarHeight = (value: number) => {
  const max = Math.max(...visitData.value.map(d => d.value))
  return (value / max) * 100
}

// 公告相关
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

const showNotice = (notice: Notice) => {
  currentNotice.value = notice
  noticeVisible.value = true
}

// 数据加载
const loadStats = async () => {
  try {
    const res = await getDashboardStats()
    stats.value = { ...stats.value, ...res }
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
}

const loadNotices = async () => {
  try {
    const res = await getLatestNotices(5)
    notices.value = res
  } catch (e) {
    console.error('获取公告失败', e)
  }
}

// 监听路由变化，记录访问历史
watch(
  () => route.path,
  (newPath, oldPath) => {
    if (oldPath && newPath !== '/dashboard' && newPath !== '/login') {
      const title = route.meta.title as string || '未知页面'
      const icon = route.meta.icon as string
      addVisitHistory(title, newPath, icon)
    }
  }
)

// 初始化
onMounted(() => {
  loadStats()
  loadNotices()

  // 加载本地存储的数据
  const savedFavorites = localStorage.getItem('dashboard-favorites')
  if (savedFavorites) {
    favoriteIds.value = JSON.parse(savedFavorites)
  }

  const savedTodos = localStorage.getItem('dashboard-todos')
  if (savedTodos) {
    todos.value = JSON.parse(savedTodos)
  }
})
</script>

<style scoped lang="scss">
.dashboard {
  padding: 0;

  .banner-card {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 0;
    }

    .banner-item {
      height: 200px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 40px;
      color: #fff;

      .banner-content {
        h2 {
          margin: 0 0 10px;
          font-size: 28px;
          font-weight: 600;
        }

        p {
          margin: 0;
          font-size: 16px;
          opacity: 0.9;
        }
      }

      .banner-image {
        opacity: 0.8;
      }
    }
  }

  .stats-row {
    margin-bottom: 16px;
  }

  .stat-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    display: flex;
    align-items: center;
    cursor: pointer;
    transition: all 0.3s;
    margin-bottom: 16px;

    &:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      transform: translateY(-2px);
    }

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 16px;

      .el-icon {
        font-size: 28px;
        color: #fff;
      }

      &.user { background: linear-gradient(135deg, #667eea, #764ba2); }
      &.role { background: linear-gradient(135deg, #f093fb, #f5576c); }
      &.menu { background: linear-gradient(135deg, #4facfe, #00f2fe); }
      &.log { background: linear-gradient(135deg, #43e97b, #38f9d7); }
    }

    .stat-info {
      flex: 1;

      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #303133;
      }

      .stat-label {
        font-size: 14px;
        color: #909399;
        margin-top: 4px;
      }

      .stat-trend {
        font-size: 12px;
        margin-top: 4px;
        display: flex;
        align-items: center;
        gap: 2px;
        height: 18px;

        &.up { color: #67c23a; }
        &.down { color: #f56c6c; }

        .el-icon { font-size: 14px; }
      }

      .stat-trend-placeholder {
        height: 18px;
        margin-top: 4px;
      }
    }
  }

  .content-row {
    margin-bottom: 16px;
  }

  .content-card {
    margin-bottom: 16px;

    &.fixed-height-card {
      :deep(.el-card__body) {
        height: 280px;
        overflow-y: auto;
      }
    }

    &.auto-height-card {
      :deep(.el-card__body) {
        height: auto;
        min-height: auto;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .card-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 500;
    }
  }

  // 常用功能
  .favorites-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
    gap: 16px;

    .favorite-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 16px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        background: #f5f7fa;
        transform: translateY(-2px);
      }

      .favorite-icon {
        width: 48px;
        height: 48px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 8px;

        .el-icon { font-size: 24px; }
      }

      .favorite-label {
        font-size: 13px;
        color: #606266;
        text-align: center;
      }
    }
  }

  // 数据对比
  .compare-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;

    .compare-item {
      text-align: center;
      padding: 16px;
      background: #f5f7fa;
      border-radius: 8px;

      .compare-label {
        font-size: 13px;
        color: #909399;
        margin-bottom: 8px;
      }

      .compare-value {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        margin-bottom: 4px;

        .current {
          font-size: 24px;
          font-weight: bold;
          color: #303133;
        }

        .compare-trend {
          font-size: 12px;
          display: flex;
          align-items: center;
          gap: 2px;
          padding: 2px 6px;
          border-radius: 4px;

          &.up {
            color: #67c23a;
            background: #f0f9eb;
          }

          &.down {
            color: #f56c6c;
            background: #fef0f0;
          }

          .el-icon {
            font-size: 12px;
          }
        }
      }

      .compare-sub {
        font-size: 12px;
        color: #c0c4cc;
      }
    }
  }

  // 图表
  .chart-container {
    padding: 20px 0;

    .bar-chart {
      display: flex;
      justify-content: space-around;
      align-items: flex-end;
      height: 180px;

      .bar-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        flex: 1;

        .bar-wrapper {
          height: 140px;
          display: flex;
          align-items: flex-end;
          width: 32px;
          position: relative;

          .bar {
            width: 100%;
            background: linear-gradient(180deg, #667eea, #764ba2);
            border-radius: 4px 4px 0 0;
            transition: height 0.5s;
            position: relative;

            &:hover {
              background: linear-gradient(180deg, #764ba2, #667eea);
            }

            &:hover + .bar-tooltip {
              opacity: 1;
            }
          }

          .bar-tooltip {
            position: absolute;
            top: -25px;
            left: 50%;
            transform: translateX(-50%);
            background: #303133;
            color: #fff;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
            opacity: 0;
            transition: opacity 0.3s;
            white-space: nowrap;
          }
        }

        .bar-label {
          margin-top: 8px;
          font-size: 12px;
          color: #909399;
        }
      }
    }
  }

  // 待办事项
  .todo-list {
    .todo-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 0;
      border-bottom: 1px solid #f0f0f0;

      &:last-child {
        border-bottom: none;
      }

      .todo-text {
        font-size: 14px;

        &.completed {
          text-decoration: line-through;
          color: #c0c4cc;
        }
      }
    }
  }

  // 公告列表
  .notice-list {
    .notice-item {
      display: flex;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      transition: background 0.3s;

      &:last-child {
        border-bottom: none;
      }

      &:hover {
        background: #f5f7fa;
      }

      .notice-title {
        flex: 1;
        margin: 0 10px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 14px;
      }

      .notice-time {
        font-size: 12px;
        color: #909399;
      }
    }
  }

  // 编辑收藏
  .edit-favorites {
    .available-functions {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;

      .function-item {
        display: flex;
        align-items: center;
        padding: 12px;
        border: 1px solid #dcdfe6;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.3s;
        position: relative;

        &:hover {
          border-color: #667eea;
        }

        &.selected {
          border-color: #67c23a;
          background: #f0f9eb;
        }

        .item-icon {
          font-size: 20px;
          margin-right: 8px;
          color: #909399;
        }

        .item-label {
          font-size: 14px;
        }

        .check-icon {
          position: absolute;
          right: 8px;
          top: 50%;
          transform: translateY(-50%);
        }
      }
    }
  }

  .notice-content {
    white-space: pre-wrap;
    line-height: 1.8;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .dashboard {
    .banner-item {
      padding: 0 20px !important;

      .banner-content h2 {
        font-size: 20px !important;
      }

      .banner-content p {
        font-size: 14px !important;
      }

      .banner-image .el-icon {
        font-size: 50px !important;
      }
    }

    .favorites-grid {
      grid-template-columns: repeat(2, 1fr) !important;
    }

    .chart-container .bar-chart {
      height: 140px !important;

      .bar-item .bar-wrapper {
        height: 100px !important;
        width: 24px !important;
      }
    }
  }
}
</style>
