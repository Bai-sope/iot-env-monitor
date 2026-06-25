<template>
  <div class="app">
    <header class="header">
      <h1>IoT 环境监测平台</h1>
      <div class="stat">
        <span>设备总数 <b>{{ stat.total }}</b></span>
        <span class="online">在线 <b>{{ stat.online }}</b></span>
        <el-button size="small" @click="loadAll">刷新</el-button>
      </div>
    </header>

    <main class="main">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="device-list">
            <template #header>设备列表</template>
            <div
              v-for="d in devices"
              :key="d.deviceId"
              class="device-item"
              :class="{ active: d.deviceId === current, offline: !d.online }"
              @click="selectDevice(d.deviceId)"
            >
              <span class="dot" :class="{ on: d.online }"></span>
              <span class="name">{{ d.deviceName || d.deviceId }}</span>
            </div>
            <el-empty v-if="!devices.length" description="暂无设备" :image-size="60" />
          </el-card>
        </el-col>

        <el-col :span="18">
          <el-card shadow="hover" class="chart-card">
            <template #header>
              <div class="card-header">
                <span>实时曲线</span>
                <el-radio-group v-model="range" size="small" @change="loadHistory">
                  <el-radio-button label="1h">1 小时</el-radio-button>
                  <el-radio-button label="6h">6 小时</el-radio-button>
                  <el-radio-button label="24h">24 小时</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div ref="chartRef" class="chart"></div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header>最新读数</template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="温度">
                {{ latest.temp != null ? latest.temp.toFixed(2) + ' °C' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="湿度">
                {{ latest.humi != null ? latest.humi.toFixed(1) + ' %RH' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="PM2.5">
                {{ latest.pm25 != null ? latest.pm25.toFixed(1) + ' μg/m³' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="电流">
                {{ latest.current != null ? latest.current.toFixed(3) + ' A' : '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="hover">
            <template #header>最近告警</template>
            <el-table :data="alerts" size="small" max-height="220">
              <el-table-column prop="metric" label="指标" width="80" />
              <el-table-column prop="message" label="内容" show-overflow-tooltip />
              <el-table-column prop="triggeredAt" label="时间" width="160" />
            </el-table>
            <el-empty v-if="!alerts.length" description="暂无告警" :image-size="50" />
          </el-card>
        </el-col>
      </el-row>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { deviceApi, telemetryApi, alertApi } from './api'

const devices = ref([])
const stat = ref({ total: 0, online: 0 })
const current = ref('')
const latest = ref({})
const alerts = ref([])
const range = ref('1h')
const chartRef = ref(null)

let chart = null
let timer = null

async function loadAll() {
  try {
    const [list, s] = await Promise.all([deviceApi.list(), deviceApi.stat()])
    devices.value = list || []
    stat.value = s || { total: 0, online: 0 }
    if (!current.value && devices.value.length) {
      selectDevice(devices.value[0].deviceId)
    }
  } catch (e) {
    console.warn('load devices failed', e)
  }
}

function selectDevice(id) {
  current.value = id
  loadLatest()
  loadHistory()
  loadAlerts()
}

async function loadLatest() {
  if (!current.value) return
  try {
    latest.value = (await telemetryApi.latest(current.value)) || {}
  } catch (e) {
    latest.value = {}
  }
}

async function loadAlerts() {
  try {
    alerts.value = (await alertApi.recent(current.value, 20)) || []
  } catch (e) {
    alerts.value = []
  }
}

async function loadHistory() {
  if (!current.value || !chart) return
  try {
    const data = await telemetryApi.history(current.value, range.value, '1m')
    const series = data.series || {}
    const fields = [
      { key: 'temp', name: '温度 (°C)' },
      { key: 'humi', name: '湿度 (%RH)' },
      { key: 'pm25', name: 'PM2.5 (μg/m³)' },
      { key: 'current', name: '电流 (A)' }
    ]

    const xAxisData = (series.temp || []).map(p => p.time.slice(11, 19))

    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: fields.map(f => f.name) },
      grid: { left: 50, right: 30, top: 40, bottom: 40 },
      xAxis: { type: 'category', data: xAxisData },
      yAxis: [
        { type: 'value', name: '温度/湿度/PM2.5' },
        { type: 'value', name: '电流 (A)', position: 'right' }
      ],
      series: fields.map((f, i) => ({
        name: f.name,
        type: 'line',
        smooth: true,
        showSymbol: false,
        yAxisIndex: f.key === 'current' ? 1 : 0,
        data: (series[f.key] || []).map(p => Number(p.value).toFixed(2))
      }))
    }, true)
  } catch (e) {
    console.warn('load history failed', e)
  }
}

onMounted(async () => {
  await nextTick()
  chart = echarts.init(chartRef.value)
  await loadAll()

  // 每 10 秒刷新一次最新值（对应设备的实际上报间隔）
  timer = setInterval(() => {
    loadLatest()
  }, 10000)

  window.addEventListener('resize', () => chart && chart.resize())
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (chart) chart.dispose()
})
</script>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, "Microsoft YaHei", sans-serif; background: #f5f7fa; }
.app { min-height: 100vh; }
.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px; background: #1f4e79; color: #fff;
}
.header h1 { margin: 0; font-size: 18px; font-weight: 600; }
.stat { display: flex; align-items: center; gap: 18px; font-size: 13px; }
.stat b { font-size: 16px; }
.stat .online b { color: #67c23a; }
.main { padding: 16px 24px; }
.device-list { height: 520px; overflow-y: auto; }
.device-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 4px; cursor: pointer;
  font-size: 13px; transition: background .15s;
}
.device-item:hover { background: #f0f6ff; }
.device-item.active { background: #e6f0ff; font-weight: 600; }
.device-item.offline { color: #999; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #dcdfe6; flex-shrink: 0; }
.dot.on { background: #67c23a; box-shadow: 0 0 4px #67c23a; }
.chart-card { height: 520px; }
.chart { width: 100%; height: 440px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
