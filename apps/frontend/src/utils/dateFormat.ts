/**
 * 日期格式化工具
 */

/**
 * 将秒级时间戳转换为 Date 对象
 * 支持秒级时间戳（后端返回）、毫秒级时间戳、字符串、Date 对象
 */
function toDate(date: string | Date | number | null | undefined): Date | null {
  if (!date) return null

  let d: Date
  if (typeof date === 'number') {
    // 判断是秒级还是毫秒级时间戳
    // 秒级时间戳通常小于 20000000000 (1970年以后, 2286年以前)
    // 毫秒级时间戳通常大于这个值
    if (date < 20000000000) {
      d = new Date(date * 1000) // 秒级转毫秒
    } else {
      d = new Date(date) // 已经是毫秒级
    }
  } else {
    d = new Date(date)
  }

  if (isNaN(d.getTime())) return null
  return d
}

export function formatDate(date: string | Date | number | null | undefined): string {
  const d = toDate(date)
  if (!d) return '-'

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

export function formatDateTime(date: string | Date | number | null | undefined): string {
  const d = toDate(date)
  if (!d) return '-'

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

export function formatDateTimeFull(date: string | Date | number | null | undefined): string {
  const d = toDate(date)
  if (!d) return '-'

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}
