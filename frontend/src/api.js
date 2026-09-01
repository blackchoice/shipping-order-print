const jsonHeaders = { 'Content-Type': 'application/json' }

async function handle(res) {
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { message: text }
  }
  if (!res.ok) {
    const msg = data?.message || res.statusText || '请求失败'
    throw new Error(msg)
  }
  return data
}

export async function listPrinters() {
  const res = await fetch('/api/printers')
  return handle(res)
}

export async function previewOrder(payload) {
  const res = await fetch('/api/print/shipping-order/preview', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  })
  return handle(res)
}

/** 打印版式，前端按此 1:1 复现纸面结果 */
export async function previewLayout(payload) {
  const res = await fetch('/api/print/shipping-order/layout', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  })
  return handle(res)
}

export async function printOrder(payload, printerName) {
  const q = printerName ? `?printerName=${encodeURIComponent(printerName)}` : ''
  const res = await fetch(`/api/print/shipping-order${q}`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  })
  return handle(res)
}

export async function downloadEscp(payload) {
  const res = await fetch('/api/print/shipping-order/preview-bytes', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  })
  if (!res.ok) {
    const text = await res.text()
    let msg = text
    try {
      msg = JSON.parse(text).message || text
    } catch {
      /* keep text */
    }
    throw new Error(msg || '导出失败')
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'shipping-order.escp.bin'
  a.click()
  URL.revokeObjectURL(url)
}

/** Client-side RMB uppercase for live UI (mirrors backend AmountToChinese for common cases). */
export function amountToChinese(amount) {
  const n = Number(amount)
  if (!Number.isFinite(n)) return ''
  const value = Math.round(n * 100) / 100
  if (value === 0) return '零元整'

  const CN_NUM = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖']
  const CN_UNIT = ['', '拾', '佰', '仟']
  const CN_SECTION = ['', '万', '亿']

  const convertSection = (section) => {
    let sb = ''
    let unitPos = 0
    let zero = false
    let x = section
    while (x > 0) {
      const digit = x % 10
      if (digit === 0) {
        if (!zero && sb.length > 0) zero = true
      } else {
        if (zero) {
          sb = '零' + sb
          zero = false
        }
        sb = CN_NUM[digit] + CN_UNIT[unitPos] + sb
      }
      x = Math.floor(x / 10)
      unitPos++
    }
    return sb
  }

  const convertInteger = (number) => {
    if (number === 0) return '零'
    let result = ''
    let sectionIndex = 0
    let zero = false
    let num = number
    while (num > 0) {
      const section = num % 10000
      if (section === 0) {
        if (!zero && result.length > 0) zero = true
      } else {
        const sectionStr = convertSection(section)
        if (zero) {
          result = '零' + result
          zero = false
        }
        result = sectionStr + CN_SECTION[sectionIndex] + result
      }
      num = Math.floor(num / 10000)
      sectionIndex++
    }
    return result
  }

  const negative = value < 0
  const abs = Math.abs(value)
  const yuan = Math.floor(abs)
  const fen = Math.round((abs - yuan) * 100)
  let sb = negative ? '负' : ''
  sb += convertInteger(yuan) + '元'
  if (fen === 0) {
    sb += '整'
  } else {
    const jiao = Math.floor(fen / 10)
    const f = fen % 10
    if (jiao > 0) sb += CN_NUM[jiao] + '角'
    else if (yuan > 0) sb += '零'
    if (f > 0) sb += CN_NUM[f] + '分'
  }
  return sb
}
