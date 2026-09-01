<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { amountToChinese, downloadEscp, listPrinters, previewLayout, printOrder } from './api'
import PrintPreview from './PrintPreview.vue'

const PREFERRED_PRINTER = 'NFCP DPK700'

const form = reactive({
  companyTitle: '怀化市兴隆农业开发有限公司出货单',
  customerName: '本部食堂',
  orderNo: 'XS-202607010206',
  date: '2026-07-01',
  deliverer: '',
  receiver: '',
  lines: [
    { productName: '牛腩', unit: '公斤', quantity: 4, unitPrice: 70, remark: '' },
    { productName: '牛腱子', unit: '公斤', quantity: 2, unitPrice: 80, remark: '' }
  ]
})

const printers = ref([])
const selectedPrinter = ref('')
const layout = ref(null)
const layoutLoading = ref(false)
const layoutError = ref('')
const status = ref('')
const error = ref('')
const busy = ref(false)

const total = computed(() =>
  form.lines.reduce((sum, row) => {
    const q = Number(row.quantity) || 0
    const p = Number(row.unitPrice) || 0
    return sum + q * p
  }, 0)
)

const totalCn = computed(() => amountToChinese(total.value))

function lineAmount(row) {
  return ((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0))
}

function payload() {
  return {
    companyTitle: form.companyTitle,
    customerName: form.customerName,
    orderNo: form.orderNo,
    date: form.date,
    deliverer: form.deliverer,
    receiver: form.receiver,
    lines: form.lines.map((row) => ({
      productName: row.productName,
      unit: row.unit,
      quantity: Number(row.quantity) || 0,
      unitPrice: Number(row.unitPrice) || 0,
      remark: row.remark || ''
    }))
  }
}

function addLine() {
  form.lines.push({ productName: '', unit: '公斤', quantity: 1, unitPrice: 0, remark: '' })
}

function removeLine(index) {
  if (form.lines.length <= 1) return
  form.lines.splice(index, 1)
}

function pickPreferredPrinter(list) {
  if (!list?.length) return ''
  const preferred = list.find((p) => p === PREFERRED_PRINTER || /DPK700|NFCP/i.test(p))
  return preferred || ''
}

async function loadPrinters() {
  try {
    const data = await listPrinters()
    printers.value = data.printers || []
    if (!selectedPrinter.value || !printers.value.includes(selectedPrinter.value)) {
      selectedPrinter.value = pickPreferredPrinter(printers.value)
    }
  } catch (e) {
    error.value = e.message
  }
}

async function doPrint() {
  if (!selectedPrinter.value) {
    error.value = '请先选择打印机（推荐 NFCP DPK700），不会发送到系统默认打印机'
    return
  }
  busy.value = true
  error.value = ''
  status.value = ''
  try {
    const data = await printOrder(payload(), selectedPrinter.value)
    status.value = `${data.message || '已提交'} → ${selectedPrinter.value}`
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

/** 预览版式来自后端，和实际打印用的是同一份行数据 */
async function refreshLayout() {
  layoutLoading.value = true
  try {
    layout.value = await previewLayout(payload())
    layoutError.value = ''
  } catch (e) {
    layoutError.value = e.message
  } finally {
    layoutLoading.value = false
  }
}

let layoutTimer = null
watch(
  () => JSON.stringify(payload()),
  () => {
    clearTimeout(layoutTimer)
    layoutTimer = setTimeout(refreshLayout, 250)
  }
)

async function doExport() {
  busy.value = true
  error.value = ''
  status.value = ''
  try {
    await downloadEscp(payload())
    status.value = '已导出 shipping-order.escp.bin'
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadPrinters(), refreshLayout()])
})
</script>

<template>
  <div class="layout">
    <header class="hero">
      <h1>出货单 ESC/P 打印</h1>
      <p>Fujitsu DPK700H · 241×93mm · 点「打印」直接发送到所选打印机</p>
    </header>

    <div class="grid">
      <section class="panel">
        <h2>单据录入</h2>

        <label>
          标题
          <input v-model="form.companyTitle" />
        </label>
        <div class="row3">
          <label>
            客户名称
            <input v-model="form.customerName" />
          </label>
          <label>
            单号
            <input v-model="form.orderNo" />
          </label>
          <label>
            日期
            <input v-model="form.date" type="date" />
          </label>
        </div>

        <div class="table-wrap">
          <table class="edit-table">
            <thead>
              <tr>
                <th>#</th>
                <th>货品名称</th>
                <th>单位</th>
                <th>数量</th>
                <th>单价</th>
                <th>金额</th>
                <th>备注</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in form.lines" :key="i">
                <td>{{ i + 1 }}</td>
                <td><input v-model="row.productName" /></td>
                <td><input v-model="row.unit" class="narrow" /></td>
                <td><input v-model.number="row.quantity" type="number" step="any" class="narrow" /></td>
                <td><input v-model.number="row.unitPrice" type="number" step="0.01" class="narrow" /></td>
                <td class="num">{{ lineAmount(row).toFixed(2) }}</td>
                <td><input v-model="row.remark" /></td>
                <td><button type="button" class="link" @click="removeLine(i)">删</button></td>
              </tr>
            </tbody>
          </table>
          <button type="button" class="secondary" @click="addLine">加一行</button>
        </div>

        <div class="row3">
          <label>
            送货人
            <input v-model="form.deliverer" />
          </label>
          <label>
            收货人
            <input v-model="form.receiver" />
          </label>
          <label>
            合计
            <div class="total">{{ total.toFixed(2) }} / {{ totalCn }}</div>
          </label>
        </div>

        <label>
          打印机
          <div class="printer-row">
            <select v-model="selectedPrinter">
              <option disabled value="">请选择打印机</option>
              <option v-for="p in printers" :key="p" :value="p">{{ p }}</option>
            </select>
            <button type="button" class="secondary" @click="loadPrinters">刷新</button>
          </div>
        </label>

        <div class="actions">
          <button type="button" class="primary" :disabled="busy" @click="doPrint">打印</button>
          <button type="button" class="secondary" :disabled="busy" @click="doExport">导出指令</button>
        </div>

        <p v-if="status" class="ok">{{ status }}</p>
        <p v-if="error" class="err">{{ error }}</p>
      </section>

      <section class="panel preview-panel">
        <h2>单据预览（与实际打印一致）</h2>
        <PrintPreview :layout="layout" :loading="layoutLoading" />
        <p v-if="layoutError" class="err">{{ layoutError }}</p>
      </section>
    </div>

  </div>
</template>

<style scoped>
.layout {
  /* 预览列加宽后，整体画布相应放宽 */
  max-width: 1600px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.hero h1 {
  margin: 0 0 6px;
  font-size: 1.6rem;
  letter-spacing: 0.04em;
}

.hero p {
  margin: 0 0 20px;
  color: #4a4638;
}

.grid {
  display: grid;
  /* 预览显示区域约 1.5×：加大右侧预览列，不改单据内字号/内边距 */
  grid-template-columns: 1fr 1.5fr;
  gap: 18px;
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: var(--panel);
  border: 1px solid #cfc7b0;
  padding: 16px 18px 20px;
}

.panel h2 {
  margin: 0 0 14px;
  font-size: 1.05rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.85rem;
  margin-bottom: 12px;
}

input,
select {
  padding: 8px 10px;
  border: 1px solid #b8b09a;
  background: #fff;
}

.row3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.table-wrap {
  overflow-x: auto;
  margin-bottom: 12px;
}

.edit-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 8px;
  font-size: 0.85rem;
}

.edit-table th,
.edit-table td {
  border-bottom: 1px solid #d5cdb8;
  padding: 6px 4px;
  text-align: left;
}

.edit-table td input {
  width: 100%;
  min-width: 64px;
  padding: 6px;
}

.edit-table td input.narrow {
  min-width: 56px;
}

.num {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  text-align: right;
}

.total {
  padding: 8px 10px;
  background: #fff;
  border: 1px dashed #b8b09a;
  min-height: 38px;
}

.printer-row {
  display: flex;
  gap: 8px;
}

.printer-row select {
  flex: 1;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

button.primary {
  background: var(--accent);
  color: #fff;
  border: none;
  padding: 10px 18px;
}

button.secondary {
  background: #fff;
  border: 1px solid #b8b09a;
  padding: 10px 14px;
}

button.link {
  background: none;
  border: none;
  color: var(--danger);
  padding: 0 4px;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ok {
  color: var(--accent);
}

.err {
  color: var(--danger);
}

.preview-panel {
  background: transparent;
  border: none;
  padding: 0;
  overflow: visible;
  min-width: 0;
}

</style>
