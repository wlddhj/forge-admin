import { test, expect } from '@playwright/test'

test.describe('大屏编辑器', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', 'admin')
    await page.fill('input[name="password"]', 'admin123')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard')
  })

  test('进入编辑器后渲染 3 栏布局', async ({ page }) => {
    await page.goto('/screen/editor/1?template=hero-3')
    await expect(page.locator('.card-panel')).toBeVisible()
    await expect(page.locator('.editor-canvas')).toBeVisible()
    await expect(page.locator('.property-panel')).toBeVisible()
  })

  test('撤销/重做按钮初始 disabled', async ({ page }) => {
    await page.goto('/screen/editor/1?template=blank')
    await expect(page.locator('button:has-text("撤销")')).toBeDisabled()
  })

  test('点击左侧数字翻牌器 → 画布新增卡片 → 右侧显示属性面板', async ({ page }) => {
    await page.goto('/screen/editor/1?template=blank')
    await page.locator('.card-item:has-text("数字翻牌器")').click()
    await expect(page.locator('.canvas-card')).toHaveCount(1)
    await expect(page.locator('.property-panel .el-form-item:has-text("标题")')).toBeVisible()
  })
})
