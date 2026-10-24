import { type Ref } from 'vue'

/**
 * 序号计算 Hook
 * 用于分页场景下计算正确的行序号
 */
export interface UseTableSeqOptions {
  /** 当前页码 */
  currentPage: Ref<number>
  /** 每页条数 */
  pageSize: Ref<number>
}

export interface UseTableSeqReturn {
  /** vxe-table seqMethod 实参为表格上下文对象（row/$rowIndex 等），此处仅消费 seq */
  seqMethod: (params: any) => number
}

export function useTableSeq(options: UseTableSeqOptions): UseTableSeqReturn {
  const { currentPage, pageSize } = options

  /**
   * vxe-table 序号计算方法
   * seq 是从 1 开始的序号（每页），需要转换为全局序号
   */
  const seqMethod = ({ seq }: { seq: number }): number => {
    return (currentPage.value - 1) * pageSize.value + seq
  }

  return {
    seqMethod
  }
}