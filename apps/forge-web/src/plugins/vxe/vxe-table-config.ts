import { VxeUI } from 'vxe-pc-ui'

/**
 * vxe-table 全局配置
 * 参考 shi9-ui-admin-vue3 的配置，适配 forge-admin 项目
 */
VxeUI.setConfig({
  table: {
    // 表格尺寸
    size: 'mini',
    // 斑马纹
    stripe: true,
    // 边框样式：none（无边框）、default（默认）、full（完整）、outer（外边框）、inner（内边框）
    border: 'none',
    // 空数据提示文本
    emptyText: '暂无数据',
    // 溢出显示方式：tooltip（tooltip提示）、ellipsis（省略号）、title（title属性）
    showOverflow: 'tooltip',
    showHeaderOverflow: 'tooltip',
    showFooterOverflow: 'tooltip',
    // 自动调整列宽
    autoResize: true,
    // 延迟 hover
    delayHover: 250,
    // 最小高度
    minHeight: 144,
    // 显示 padding
    padding: true,

    // 行配置
    rowConfig: {
      // 行数据的唯一主键字段名
      keyField: '_X_ROW_KEY',
      // 鼠标悬停高亮
      isHover: true,
      // 当前行高亮
      isCurrent: true
    },

    // 列配置
    columnConfig: {
      // 最大固定列数
      maxFixedSize: 4,
      // 列宽可调整
      resizable: true,
      // 使用 key
      useKey: true
    },

    // 复选框配置
    checkboxConfig: {
      // 严格模式
      strict: true,
      // 范围选择
      range: true,
      // Shift 键多选
      isShiftKey: true,
      // 高亮
      highlight: false
    },

    // 单选配置
    radioConfig: {
      strict: true
    },

    // Tooltip 配置
    tooltipConfig: {
      enterable: true
    },

    // 验证配置
    validConfig: {
      showMessage: true,
      autoClear: true,
      autoPos: true,
      message: 'inline',
      msgMode: 'single',
      theme: 'normal'
    },

    // 自定义列配置（允许用户自定义显示/隐藏列、列宽、固定等）
    customConfig: {
      // 允许显示/隐藏
      allowVisible: true,
      // 模式：modal（弹窗）、simple（简单）
      mode: 'modal',
      // 允许调整列宽
      allowResizable: true,
      // 允许固定列
      allowFixed: true,
      // 允许排序
      allowSort: true,
      // 显示底部
      showFooter: true,
      // 位置
      placement: 'top-right',
      // 弹窗配置
      modalOptions: {
        showMaximize: true,
        mask: true,
        lockView: true,
        resize: true,
        escClosable: true
      },
      storage:{visible:true,resizable:true,sort:true,fixed:true},
    },

    // 排序配置
    sortConfig: {
      // 远程排序（false 表示本地排序）
      remote: false,
      // 触发方式：cell（点击单元格）、header（点击表头）
      trigger: 'cell',
      // 排序顺序
      orders: ['asc', 'desc', null],
      // 显示图标
      showIcon: true,
      // 图标布局
      iconLayout: 'vertical'
    },

    // 导入配置
    importConfig: {},
    // 导出配置
    exportConfig: {},
    // 打印配置
    printConfig: {},

    // 鼠标配置
    mouseConfig: {
      // 选中
      selected: true,
      // 扩展
      extension: true
    },

    // 键盘配置
    keyboardConfig: {
      isArrow: true,
      isEnter: true,
      isTab: true,
      isDel: true,
      isEdit: true
    },

    // 区域配置
    areaConfig: {
      autoClear: true,
      selectCellByHeader: true
    },

    // 剪贴板配置
    clipConfig: {
      isCopy: true,
      isCut: true,
      isPaste: true
    },

    // 查找替换配置
    fnrConfig: {
      isFind: true,
      isReplace: true
    },

    // 横向虚拟滚动
    scrollX: {
      enabled: true,
      gt: 0
    },

    // 纵向虚拟滚动
    scrollY: {
      enabled: true,
      gt: 100,
      oSize: 0,
      immediate: true,
      scrollToTopOnChange: true
    }
  },

  // 工具栏配置
  toolbar: {
    // 导入配置
    import: {
      mode: 'covering'
    },
    // 导出配置
    export: {
      types: ['csv', 'html', 'xml', 'txt']
    }
  }
})

export default VxeUI