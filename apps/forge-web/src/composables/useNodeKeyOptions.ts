import type { FlowlongNodeModel } from './useFlowLongDataTransform'

export interface NodeKeyOption {
  value: string
  label: string
  type?: number
}

/**
 * 收集流程模型中的节点 key 选项（供路由分支目标、驳回策略"指定节点"共用）
 *
 * @param root 模型根节点
 * @param excludeKey 排除该节点及其整个子树（避免路由/驳回目标指向自身内部形成环）
 */
export function useNodeKeyOptions() {
  const collect = (root: FlowlongNodeModel | null | undefined, excludeKey?: string): NodeKeyOption[] => {
    const result: NodeKeyOption[] = []
    const walk = (node: FlowlongNodeModel | undefined | null) => {
      if (!node || node.nodeKey === excludeKey) return
      if (node.nodeKey) {
        result.push({ value: node.nodeKey, label: `${node.nodeName}（${node.nodeKey}）`, type: node.type })
      }
      walk(node.childNode)
      const branchLists = [node.conditionNodes, node.parallelNodes, node.inclusiveNodes, node.routeNodes]
      branchLists.forEach(list => list?.forEach(cn => walk(cn.childNode)))
    }
    walk(root)
    return result
  }

  return { collect }
}
