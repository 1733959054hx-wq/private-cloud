/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'spark-md5' {
  class SparkMD5 {
    constructor()
    append(str: string): SparkMD5
    end(raw?: boolean): string
    reset(): SparkMD5
    getState(): SparkMD5.State
    setState(state: SparkMD5.State): SparkMD5
    destroy(): void
    static hash(str: string, raw?: boolean): string
  }

  namespace SparkMD5 {
    interface State {
      buff: Uint8Array
      length: number
      hash: number[]
    }

    class ArrayBuffer {
      constructor()
      append(arr: globalThis.ArrayBuffer): ArrayBuffer
      end(raw?: boolean): string
      reset(): ArrayBuffer
      getState(): State
      setState(state: State): ArrayBuffer
      destroy(): void
      static hash(arr: globalThis.ArrayBuffer, raw?: boolean): string
    }
  }

  export default SparkMD5
}

declare module 'sockjs-client/dist/sockjs' {
  class SockJS {
    constructor(url: string, protocols?: string | string[], options?: any)
    readyState: number
    send(message: string): void
    close(code?: number, reason?: string): void
    onopen: ((event: Event) => void) | null
    onmessage: ((event: { data: string }) => void) | null
    onclose: ((event: { code: number; reason: string }) => void) | null
  }
  export default SockJS
}

declare module 'docx-preview' {
  export function renderAsync(
    data: Blob | ArrayBuffer | Uint8Array,
    bodyContainer: HTMLElement,
    styleContainer?: HTMLElement | null,
    options?: any
  ): Promise<void>
}

declare module 'markdown-it' {
  interface Options {
    html?: boolean
    linkify?: boolean
    typographer?: boolean
    breaks?: boolean
  }
  class MarkdownIt {
    constructor(options?: Options)
    enable(rule: string): this
    render(src: string): string
  }
  export default MarkdownIt
}
