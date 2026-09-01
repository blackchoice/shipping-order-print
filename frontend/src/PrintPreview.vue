<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  layout: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const MM_PX = 96 / 25.4
/** 竖线/横线的笔画粗细，接近 24 针的线宽 */
const STROKE_MM = 0.28
/**
 * 24 针字身高 24 点、纵向 180 dpi → 3.39mm，比常用行距（20~22/180 英寸）还高。
 * 字符是自行首向下打的，上下行因此略有重叠，竖线才连成实线——预览必须照这个来算，
 * 否则字会向上溢出、竖线也会在格子底部断开。
 */
const CELL_HEIGHT_MM = (24 / 180) * 25.4

const BOX_STROKES = {
  '│': { up: true, down: true },
  '─': { left: true, right: true },
  '┌': { down: true, right: true },
  '┐': { down: true, left: true },
  '└': { up: true, right: true },
  '┘': { up: true, left: true },
  '├': { up: true, down: true, right: true },
  '┤': { up: true, down: true, left: true },
  '┬': { left: true, right: true, down: true },
  '┴': { left: true, right: true, up: true },
  '┼': { up: true, down: true, left: true, right: true }
}

/** 与后端 displayWidth 一致：非 ASCII 占 2 列 */
function colsOf(ch) {
  return ch.codePointAt(0) > 0x7f ? 2 : 1
}

const model = computed(() => {
  const l = props.layout
  if (!l || !Array.isArray(l.lines)) return null

  const cpi = l.cpi || 12
  const charW = 25.4 / cpi
  const gridW = (l.cols || 0) * charW
  // 预览在纸面上居中；printLeftMarginMm 只给打印机 ESC l 补偿，不参与定位
  const marginX = Math.max(0, (l.widthMm - gridW) / 2)

  let y = l.topMarginMm || 0
  let bottomMm = 0
  const lines = l.lines.map((line, i) => {
    const cellW = line.doubleWidth ? charW * 2 : charW
    const cellH = line.doubleHeight ? CELL_HEIGHT_MM * 2 : CELL_HEIGHT_MM
    // 全角字宽 = 2 个半角，字身却矮于此，所以 em 方框要纵向压到字身高
    const fontMm = cellW * 2
    const scaleY = cellH / fontMm

    const glyphs = []
    const rules = []
    let col = 0
    for (const ch of line.text || '') {
      const w = colsOf(ch)
      const box = BOX_STROKES[ch]
      if (box) {
        rules.push({ x: col * cellW, w: w * cellW, ...box })
      } else if (ch !== ' ' && ch !== '\u3000') {
        glyphs.push({ ch, x: col * cellW, w: w * cellW })
      }
      col += w
    }

    const item = {
      key: i,
      top: y,
      cellH,
      fontMm,
      scaleY,
      bold: line.bold,
      glyphs,
      rules
    }
    bottomMm = Math.max(bottomMm, y + cellH)
    y += line.heightMm + (line.extraFeedMm || 0)
    return item
  })

  // 表格内容行：在上下横线之间的格子内竖直居中
  for (let i = 0; i < lines.length; i++) {
    const ln = lines[i]
    if (!ln.glyphs.length) continue
    const text = l.lines[i]?.text || ''
    if (!text.startsWith('│')) continue

    let pi = i - 1
    while (pi >= 0 && !/^[┌├]/.test(l.lines[pi].text)) pi--
    let ni = i + 1
    while (ni < lines.length && !/^[├└]/.test(l.lines[ni].text)) ni++
    if (pi < 0 || ni >= lines.length) continue

    // 横线画在字身正中，格子的上下沿因此是相邻横线行的 cellH/2 处
    const cellTop = lines[pi].top + lines[pi].cellH / 2
    const cellBottom = lines[ni].top + lines[ni].cellH / 2
    const glyphH = ln.fontMm * ln.scaleY
    ln.vOffset = cellTop + (cellBottom - cellTop - glyphH) / 2 - ln.top
  }

  return {
    charW,
    marginX,
    gridW,
    lines,
    topMarginMm: l.topMarginMm || 0,
    usedMm: bottomMm,
    widthMm: l.widthMm,
    heightMm: l.heightMm,
    cpi,
    cols: l.cols,
    lineSpacingN: l.lineSpacingN,
    overflow: bottomMm > l.heightMm + 0.5
  }
})

const host = ref(null)
const zoom = ref(1)
let observer = null

function fit() {
  const el = host.value
  const m = model.value
  if (!el || !m) return
  const available = el.clientWidth
  if (available <= 0) return
  zoom.value = Math.min(1.6, available / (m.widthMm * MM_PX))
}

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined' && host.value) {
    observer = new ResizeObserver(fit)
    observer.observe(host.value)
  }
  fit()
})

onBeforeUnmount(() => observer?.disconnect())

const paperStyle = computed(() => {
  const m = model.value
  if (!m) return {}
  return {
    width: `${m.widthMm}mm`,
    height: `${m.heightMm}mm`,
    transform: `scale(${zoom.value})`
  }
})

const boxStyle = computed(() => {
  const m = model.value
  if (!m) return {}
  return {
    width: `${m.widthMm * MM_PX * zoom.value}px`,
    height: `${m.heightMm * MM_PX * zoom.value}px`
  }
})

function ruleV(line, rule) {
  if (!rule.up && !rule.down) return null
  // 竖笔就是字身里的那一竖：整根贯穿字身，半根从字身中线（横线所在处）起止。
  // 接缝由 ┬/┴/┼ 的选取保证，这里不做任何补偿。
  const half = line.cellH / 2
  const top = rule.up ? 0 : half
  const height = rule.up && rule.down ? line.cellH : half
  return {
    left: `${rule.x + rule.w / 2 - STROKE_MM / 2}mm`,
    top: `${top}mm`,
    height: `${height}mm`,
    width: `${STROKE_MM}mm`
  }
}

function ruleH(line, rule) {
  if (!rule.left && !rule.right) return null
  const left = rule.left ? rule.x : rule.x + rule.w / 2
  const width = rule.left && rule.right ? rule.w : rule.w / 2
  return {
    left: `${left}mm`,
    width: `${width}mm`,
    top: `${line.cellH / 2 - STROKE_MM / 2}mm`,
    height: `${STROKE_MM}mm`
  }
}

function glyphStyle(line, glyph) {
  return {
    left: `${glyph.x}mm`,
    width: `${glyph.w}mm`,
    top: `${line.vOffset ?? 0}mm`,
    fontSize: `${line.fontMm}mm`,
    fontWeight: line.bold ? 700 : 400,
    transform: `scaleY(${line.scaleY})`
  }
}
</script>

<template>
  <div class="preview">
    <div ref="host" class="viewport" :style="boxStyle">
      <div v-if="model" class="paper" :style="paperStyle">
        <div class="grid" :style="{ left: `${model.marginX}mm`, width: `${model.gridW}mm` }">
          <div
            v-for="line in model.lines"
            :key="line.key"
            class="line"
            :style="{ top: `${line.top}mm` }"
          >
            <template v-for="(rule, ri) in line.rules" :key="`r${ri}`">
              <i v-if="ruleV(line, rule)" class="stroke" :style="ruleV(line, rule)" />
              <i v-if="ruleH(line, rule)" class="stroke" :style="ruleH(line, rule)" />
            </template>
            <span
              v-for="(glyph, gi) in line.glyphs"
              :key="`g${gi}`"
              class="glyph"
              :style="glyphStyle(line, glyph)"
              >{{ glyph.ch }}</span
            >
          </div>
        </div>
      </div>
      <p v-else class="empty">{{ loading ? '正在生成版式…' : '暂无版式数据' }}</p>
    </div>

    <p v-if="model" class="meta">
      {{ model.widthMm }}×{{ model.heightMm }}mm · {{ model.cpi }} CPI · {{ model.cols }} 列 · 行距
      {{ model.lineSpacingN }}/180" · 已用 {{ model.usedMm.toFixed(1) }}mm ·
      {{ Math.round(zoom * 100) }}%
      <span v-if="model.overflow" class="warn">内容超出单页高度</span>
    </p>
  </div>
</template>

<style scoped>
.preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.viewport {
  position: relative;
  overflow: hidden;
}

.paper {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
  background: #fffdf6;
  border: 1px solid #cfc7b0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  box-sizing: border-box;
}

.grid {
  position: absolute;
  top: 0;
}

.line {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
}

.stroke {
  position: absolute;
  background: #1c1a14;
}

.glyph {
  position: absolute;
  transform-origin: top center;
  text-align: center;
  white-space: pre;
  line-height: 1;
  color: #1c1a14;
  /* 汉字宽度等于半角两倍的等宽 CJK 字体，才能和字符网格对上 */
  font-family: 'NSimSun', 'SimSun', 'MS Song', 'Sarasa Mono SC', 'Noto Sans Mono CJK SC', monospace;
}

.empty {
  margin: 0;
  padding: 24px 0;
  color: #6b6552;
  font-size: 0.85rem;
}

.meta {
  margin: 0;
  color: #6b6552;
  font-size: 0.75rem;
}

.warn {
  margin-left: 8px;
  color: #a3341f;
  font-weight: 600;
}
</style>
