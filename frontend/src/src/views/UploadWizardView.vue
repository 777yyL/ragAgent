<template>
  <div class="page-container">
    <div class="page-title">上传新知识</div>
    <div class="page-sub">按引导完成知识采集 → 加工 → 入库 → 审核</div>

    <el-card shadow="never">
      <el-steps :active="activeStep" finish-status="success" style="margin-bottom:24px">
        <el-step title="知识采集与抽取" description="上传文件" />
        <el-step title="知识处理与加工" description="解析切片配置" />
        <el-step title="知识入库" description="元数据填写" />
        <el-step title="知识审核" description="提交审核" />
      </el-steps>

      <!-- Step 1: 上传 -->
      <div v-show="activeStep === 0">
        <el-upload drag :before-upload="onFileSelect" :auto-upload="false" :file-list="fileList" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.html">
          <el-icon size="48" color="var(--text-3)"><UploadFilled /></el-icon>
          <div style="margin-top:8px">拖拽文件到此或<em style="color:var(--primary)">点击上传</em></div>
          <template #tip>支持 PDF / Word / Excel / PPT / TXT / CSV / HTML，单文件 ≤200MB</template>
        </el-upload>
        <div style="margin-top:16px;text-align:right">
          <el-button type="primary" :disabled="!selectedFile" @click="activeStep = 1">下一步：切片配置 →</el-button>
        </div>
      </div>

      <!-- Step 2: 切片配置 -->
      <div v-show="activeStep === 1">
        <el-form label-width="140px" size="default">
          <el-form-item label="目标知识库">
            <el-select v-model="uploadForm.datasetId" placeholder="选择知识库" style="width:100%">
              <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
            </el-select>
          </el-form-item>
          <el-alert type="info" :closable="false" style="margin-bottom:16px">
            以下切片配置能力由 RAGFlow DeepDoc 引擎提供。系统将自动解析、切分，并生成关键词/问题。
          </el-alert>
          <el-form-item label="切分方法">
            <el-select v-model="uploadForm.chunkMethod" style="width:200px">
              <el-option label="通用 (naive)" value="naive" />
              <el-option label="问答 (qa)" value="qa" />
              <el-option label="法规 (laws)" value="laws" />
              <el-option label="手册 (manual)" value="manual" />
            </el-select>
          </el-form-item>
          <el-form-item label="分段Token数"><el-slider v-model="uploadForm.chunkTokenNum" :min="128" :max="2048" show-input style="width:400px" /></el-form-item>
          <el-form-item label="自动关键词"><el-input-number v-model="uploadForm.autoKeywords" :min="0" :max="32" /></el-form-item>
          <el-form-item label="自动问题"><el-input-number v-model="uploadForm.autoQuestions" :min="0" :max="10" /></el-form-item>
        </el-form>
        <div style="text-align:right">
          <el-button @click="activeStep = 0">← 上一步</el-button>
          <el-button type="primary" :disabled="!uploadForm.datasetId" @click="activeStep = 2">下一步：元数据 →</el-button>
        </div>
      </div>

      <!-- Step 3: 元数据 -->
      <div v-show="activeStep === 2">
        <el-form label-width="140px" size="default">
          <el-form-item label="知识标题"><el-input v-model="metaForm.title" placeholder="自动填充文件名" /></el-form-item>
          <el-form-item label="业务分类">
            <el-select v-model="metaForm.businessType" style="width:100%">
              <el-option label="运行规程" value="REGULATION" /><el-option label="行业标准" value="STANDARD" />
              <el-option label="设备台账" value="LEDGER" /><el-option label="故障案例" value="CASE" />
              <el-option label="告警事件" value="ALARM" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-select v-model="metaForm.tags" multiple filterable allow-create placeholder="输入标签回车" style="width:100%">
              <el-option label="锅炉" value="锅炉" /><el-option label="汽轮机" value="汽轮机" />
              <el-option label="发电机" value="发电机" /><el-option label="环保" value="环保" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据密级">
            <el-radio-group v-model="metaForm.securityLevel">
              <el-radio :label="1">公开</el-radio><el-radio :label="2">内部</el-radio>
              <el-radio :label="3">秘密</el-radio><el-radio :label="4">机密</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <div style="text-align:right">
          <el-button @click="activeStep = 1">← 上一步</el-button>
          <el-button type="primary" @click="activeStep = 3">下一步：提交审核 →</el-button>
        </div>
      </div>

      <!-- Step 4: 提交审核 -->
      <div v-show="activeStep === 3">
        <el-alert type="success" :closable="false" style="margin-bottom:16px">
          确认提交后，系统将自动执行：上传 → 解析 → AI 预审 → 进入三级审核流程
        </el-alert>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件">{{ selectedFile?.name }}</el-descriptions-item>
          <el-descriptions-item label="知识库">{{ datasets.find(d => d.id === uploadForm.datasetId)?.name }}</el-descriptions-item>
          <el-descriptions-item label="切分方法">{{ uploadForm.chunkMethod }}</el-descriptions-item>
          <el-descriptions-item label="业务分类">{{ metaForm.businessType }}</el-descriptions-item>
          <el-descriptions-item label="密级">{{ ['','公开','内部','秘密','机密'][metaForm.securityLevel] }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ metaForm.tags.join(', ') }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:24px;text-align:right">
          <el-button @click="activeStep = 2">← 上一步</el-button>
          <el-button type="primary" size="large" @click="onSubmit" :loading="submitting">提交审核</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { datasetApi, documentApi, auditApi, type Dataset } from '@/api'

const router = useRouter()
const activeStep = ref(0)
const submitting = ref(false)
const selectedFile = ref<File | null>(null)
const fileList = ref<any[]>([])
const datasets = ref<Dataset[]>([])

const uploadForm = reactive({
  datasetId: '', chunkMethod: 'naive', chunkTokenNum: 512,
  autoKeywords: 5, autoQuestions: 3,
})

const metaForm = reactive({
  title: '', businessType: 'REGULATION',
  tags: [] as string[], securityLevel: 2,
})

function onFileSelect(file: File) {
  selectedFile.value = file
  metaForm.title = file.name.replace(/\.[^.]+$/, '')
  fileList.value = [{ name: file.name, url: '' }]
  return false
}

async function onSubmit() {
  if (!selectedFile.value || !uploadForm.datasetId) { ElMessage.warning('请完成所有步骤'); return }
  submitting.value = true
  try {
    await documentApi.upload(uploadForm.datasetId, selectedFile.value, true)
    ElMessage.success('上传成功，已进入解析 + 审核流程')
    router.push('/workbench')
  } catch (e: any) { ElMessage.error('提交失败: ' + e.message) }
  finally { submitting.value = false }
}

onMounted(async () => {
  try {
    const resp = await datasetApi.list(1, 50)
    datasets.value = resp?.datasets || []
  } catch { /* 静默 */ }
})
</script>
