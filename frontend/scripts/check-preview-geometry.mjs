/**
 * 校验预览的几何模型：字符不越出纸面、竖线在相邻行之间连续、格高比例正确。
 * 用法：node scripts/check-preview-geometry.mjs http://localhost:8080
 */
const base = process.argv[2] || 'http://localhost:8080'
const CELL_HEIGHT_MM = (24 / 180) * 25.4
const VBARS = new Set(['│', '┌', '┐', '└', '┘', '├', '┤', '┬', '┴', '┼'])
const HAS_UP = new Set(['│', '└', '┘', '├', '┤', '┴', '┼'])
const HAS_DOWN = new Set(['│', '┌', '┐', '├', '┤', '┬', '┼'])

const payload = {
  companyTitle: '怀化市兴隆农业开发有限公司出货单',
  customerName: '本部食堂',
  orderNo: 'XS-202607010206',
  date: '2026-07-01',
  receiver: '周高玉',
  lines: [
    { productName: '牛腩', unit: '公斤', quantity: 4, unitPrice: 70 },
    { productName: '牛腱子', unit: '公斤', quantity: 2, unitPrice: 80 }
  ]
}

const res = await fetch(`${base}/api/print/shipping-order/layout`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload)
})
if (!res.ok) {
  console.error(`layout 请求失败: ${res.status}`)
  process.exit(1)
}
const layout = await res.json()

const charW = 25.4 / layout.cpi
let y = layout.topMarginMm || 0
const rows = layout.lines.map((line) => {
  const cellW = line.doubleWidth ? charW * 2 : charW
  const cellH = line.doubleHeight ? CELL_HEIGHT_MM * 2 : CELL_HEIGHT_MM
  const bars = new Map()
  let col = 0
  for (const ch of line.text) {
    if (VBARS.has(ch)) {
      bars.set(+(col * cellW + cellW).toFixed(3), {
        up: HAS_UP.has(ch),
        down: HAS_DOWN.has(ch)
      })
    }
    col += ch.codePointAt(0) > 0x7f ? 2 : 1
  }
  const row = { top: y, cellH, bars, advance: line.heightMm, text: line.text }
  y += line.heightMm + (line.extraFeedMm || 0)
  return row
})

const problems = []

const bottomMm = Math.max(...rows.map((r) => r.top + r.cellH))
if (rows[0].top < 0) problems.push('首行起点在纸面之上')
if (Math.abs(rows[0].top - layout.topMarginMm) > 1e-9) problems.push('标题未按上边距下移')
if (bottomMm > layout.heightMm) problems.push(`内容底部 ${bottomMm.toFixed(2)}mm 超出 ${layout.heightMm}mm`)

// 竖线收口：逐列把笔画区间并起来，必须从首条横线连到末条横线，既不断开也不越出
const columns = new Map()
rows.forEach((row, i) => {
  const mid = row.top + row.cellH / 2
  for (const [x, stroke] of row.bars) {
    const seg = {
      from: stroke.up ? row.top : mid,
      to: stroke.down ? row.top + row.cellH : mid,
      row: i
    }
    if (!columns.has(x)) columns.set(x, { segs: [], mids: [] })
    const c = columns.get(x)
    c.segs.push(seg)
    c.mids.push(mid)
  }
})

const EPS = 0.01
const sortedX = [...columns.keys()].sort((a, b) => a - b)
sortedX.forEach((x, n) => {
  const { segs, mids } = columns.get(x)
  segs.sort((a, b) => a.from - b.from)
  const head = Math.min(...mids)
  const tail = Math.max(...mids)
  const label = `第 ${n + 1} 根竖线 (x=${x}mm)`
  if (segs[0].from < head - EPS) {
    problems.push(`${label} 顶端越出横线 ${(head - segs[0].from).toFixed(2)}mm`)
  }
  let reach = segs[0].to
  for (const seg of segs.slice(1)) {
    if (seg.from > reach + EPS) {
      problems.push(`${label} 在第 ${seg.row} 行前断开 ${(seg.from - reach).toFixed(2)}mm`)
    }
    reach = Math.max(reach, seg.to)
  }
  if (reach > tail + EPS) {
    problems.push(`${label} 底端越出横线 ${(reach - tail).toFixed(2)}mm`)
  }
})

// 格高：可视高度 = 相邻两条横线之间的走纸量
const ruleIdx = rows.map((r, i) => (/^[┌├└]/.test(r.text) ? i : -1)).filter((i) => i >= 0)
const cells = []
for (let k = 0; k < ruleIdx.length - 1; k++) {
  let h = 0
  for (let i = ruleIdx[k]; i < ruleIdx[k + 1]; i++) h += rows[i].advance
  cells.push({ from: ruleIdx[k], height: h })
}
const signRuleIdx = rows.findIndex((r) => r.text.includes('送货人')) - 2
const signCell = cells.find((c) => c.from === signRuleIdx)
const dataCell = cells[0]
const ratio = signCell.height / dataCell.height

console.log(`纸张 ${layout.widthMm}×${layout.heightMm}mm  ${layout.cpi} CPI  行距 ${layout.lineSpacingN}/180"`)
console.log(`字身 ${CELL_HEIGHT_MM.toFixed(2)}mm > 行距 ${layout.lineHeightMm.toFixed(2)}mm → 相邻行重叠 ${(CELL_HEIGHT_MM - layout.lineHeightMm).toFixed(2)}mm`)
console.log(`上边距 ${layout.topMarginMm.toFixed(2)}mm  内容占用 ${rows[0].top.toFixed(2)} → ${bottomMm.toFixed(2)}mm`)
console.log(
  `货物格 ${dataCell.height.toFixed(2)}mm  送货人格 ${signCell.height.toFixed(2)}mm  比例 ${ratio.toFixed(3)}`
)
if (Math.abs(ratio - 1.5) > 1e-6) problems.push(`送货人格不是 1.5 倍（${ratio.toFixed(3)}）`)

if (problems.length) {
  console.error('\n不合格：')
  for (const p of problems) console.error(` - ${p}`)
  process.exit(1)
}
console.log('\n通过：无溢出、格高正确，' + sortedX.length + ' 根竖线逐根收口（不断开、不越出）')
