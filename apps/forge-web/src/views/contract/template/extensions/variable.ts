import { Node, mergeAttributes } from '@tiptap/core'

/**
 * 合同变量芯片节点：inline 原子节点，不可拆分；
 * 序列化 <span data-field="key" class="ct-var">label</span>，与后端 ContractHtmlProcessor 的 data-field 契约一致
 */
export interface VariableNodeAttrs {
  fieldKey: string
  label: string
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    variable: {
      /** 在光标处插入变量芯片 */
      insertVariable: (attrs: VariableNodeAttrs) => ReturnType
    }
  }
}

export const VariableNode = Node.create({
  name: 'variable',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return {
      fieldKey: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-field')
      },
      label: {
        default: '',
        parseHTML: (element) => element.textContent ?? ''
      }
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-field]' }]
  },

  renderHTML({ node, HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-field': node.attrs.fieldKey,
        class: 'ct-var'
      }),
      node.attrs.label ?? node.attrs.fieldKey
    ]
  },

  addCommands() {
    return {
      insertVariable:
        (attrs: VariableNodeAttrs) =>
        ({ commands }) =>
          commands.insertContent({ type: this.name, attrs })
    }
  }
})

/** 校验变量 key：仅字母数字下划线（作为编号段/JSON key 使用） */
export const isValidFieldKey = (key: string): boolean => /^[A-Za-z0-9_]+$/.test(key)
