export function useWatermark() {
  let watermarkEl: HTMLElement | null = null

  function createWatermark(text: string, container: HTMLElement): HTMLElement {
    const canvas = document.createElement('canvas')
    canvas.width = 240
    canvas.height = 140

    const ctx = canvas.getContext('2d')!
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    ctx.font = '13px Microsoft YaHei, sans-serif'
    ctx.fillStyle = 'rgba(0, 0, 0, 0.15)'
    ctx.rotate(-0.2)
    ctx.textAlign = 'left'
    ctx.textBaseline = 'top'

    const lines = text.split('\n')
    lines.forEach((line, i) => {
      ctx.fillText(line, 10, 20 + i * 20)
    })

    const div = document.createElement('div')
    div.className = '__watermark-overlay'
    div.style.cssText =
      'position:absolute;top:0;left:0;width:100%;height:100%;' +
      'pointer-events:none;z-index:100;' +
      'background-image:url(' + canvas.toDataURL('image/png') + ');' +
      'background-repeat:repeat;background-size:240px 140px;'

    container.style.position = 'relative'
    container.appendChild(div)
    return div
  }

  function setWatermark(text?: string, container?: HTMLElement | null) {
    clearWatermark()
    // 如果没有传入 container，不创建水印（避免水印被错误地加到 document.body 上变成全局水印）
    if (!container) {
      console.warn('[useWatermark] setWatermark 调用时 container 为空，跳过水印创建')
      return
    }
    const target = container

    const now = new Date()
    const dateStr =
      now.getFullYear() + '-' +
      String(now.getMonth() + 1).padStart(2, '0') + '-' +
      String(now.getDate()).padStart(2, '0')
    const timeStr =
      String(now.getHours()).padStart(2, '0') + ':' +
      String(now.getMinutes()).padStart(2, '0')

    const userName = text || '用户'
    const content = userName + '\n' + dateStr + ' ' + timeStr
    watermarkEl = createWatermark(content, target)
  }

  function clearWatermark() {
    if (watermarkEl) {
      try {
        watermarkEl.parentNode?.removeChild(watermarkEl)
      } catch { /* ignore */ }
      watermarkEl = null
    }
  }

  function enableScreenshotProtection() {
    document.addEventListener('contextmenu', (e) => e.preventDefault())

    document.addEventListener('keydown', (e) => {
      if (
        e.key === 'F12' ||
        (e.ctrlKey && e.shiftKey && (e.key === 'I' || e.key === 'i')) ||
        (e.ctrlKey && e.shiftKey && (e.key === 'J' || e.key === 'j')) ||
        (e.ctrlKey && (e.key === 'U' || e.key === 'u'))
      ) {
        e.preventDefault()
      }
    })

    document.addEventListener('keyup', (e) => {
      if (e.key === 'PrintScreen') {
        e.preventDefault()
      }
    })
  }

  return { setWatermark, clearWatermark, enableScreenshotProtection }
}
