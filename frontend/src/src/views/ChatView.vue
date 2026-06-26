<template>
  <div class="page-container chat-page">
    <div class="page-title">智能对话</div>
    <div class="page-sub">对应 RAGFlow Chat Assistant + Session 接口，含 OpenAI-Compatible chat completion 与溯源展示</div>

    <div class="chat-wrap">
      <!-- 左侧：助手与会话 -->
      <el-card shadow="never" class="chat-left" body-style="padding:12px;display:flex;flex-direction:column;height:100%">
        <div class="asst-block">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
            <span class="asst-block-label">助手</span>
            <el-button text size="small" type="primary" @click="showCreateAssistant = true">+ 创建助手</el-button>
          </div>
          <el-select v-model="currentAssistantId" placeholder="选择助手" style="width:100%" @change="onAssistantChange">
            <el-option v-for="a in assistants" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
          <el-button v-if="currentAssistantId" text size="small" type="danger" @click="onDeleteAssistant" style="margin-top:4px">删除当前助手</el-button>
          <div class="asst-meta" v-if="currentAssistant">
            <span class="api-tag">model: {{ currentAssistant.llmModel || 'qwen2.5-14b' }}</span>
            <span class="api-tag">temp: {{ currentAssistant.temperature ?? 0.3 }}</span>
            <span class="api-tag">top_k: {{ currentAssistant.topK ?? 8 }}</span>
          </div>
        </div>

        <div class="left-head">
          <span class="left-head-title">会话历史</span>
          <el-button size="small" type="primary" @click="createSession" :disabled="!currentAssistantId">+ 新建会话</el-button>
        </div>
        <div class="sess-list">
          <div v-for="s in sessions" :key="s.id"
            class="sess-item" :class="{ active: currentSessionId === s.id }"
            @click="switchSession(s.id)">
            <div class="sess-title">{{ s.name || s.preview || '新会话' }}</div>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <div class="sess-time">{{ s.updatedAt || s.time || '-' }}</div>
              <el-button text size="small" type="danger" @click.stop="onDeleteSession(s.id)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!sessions.length" description="暂无会话" :image-size="48" />
        </div>
      </el-card>

      <!-- 中间：对话区 -->
      <el-card shadow="never" class="chat-mid" body-style="padding:0;display:flex;flex-direction:column;height:100%">
        <div class="chat-top">
          <span class="chat-top-name">{{ currentAssistant?.name || '请选择助手' }}</span>
          <template v-if="currentAssistant?.datasets?.length">
            <el-tag v-for="kb in currentAssistant.datasets" :key="kb" size="small" type="info" effect="plain">
              {{ kb }}
            </el-tag>
          </template>
        </div>

        <div ref="msgListRef" class="msg-list">
          <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
            <div class="msg-avatar" :class="msg.role">
              {{ msg.role === 'user' ? '我' : assistantInitial }}
            </div>
            <div class="msg-body">
              <div class="msg-bubble" v-html="renderContent(msg.content)"></div>

              <!-- 溯源 chunks -->
              <el-collapse v-if="msg.reference?.chunks?.length" class="source-collapse" @change="(v: any) => onCollapseChange(v, idx)">
                <el-collapse-item :name="idx">
                  <template #title>
                    <span class="sources-title">溯源 chunk（{{ msg.reference.chunks.length }}）</span>
                  </template>
                  <div v-for="(chunk, ci) in msg.reference.chunks" :key="ci" class="source-item">
                    <div class="source-head">
                      <div class="source-doc">{{ chunk.documentName || chunk.docName || '未知文档' }}</div>
                      <el-tag size="small" :type="similarityType(chunk.similarity)">
                        {{ Number(chunk.similarity).toFixed(2) }}
                      </el-tag>
                    </div>
                    <div class="source-snippet">{{ chunk.content }}</div>
                  </div>
                </el-collapse-item>
              </el-collapse>

              <div class="msg-actions">
                <el-button link size="small" @click="copyMessage(msg.content)">复制</el-button>
                <el-button link size="small" @click="regenerate(idx)" v-if="msg.role === 'assistant'">重新生成</el-button>
              </div>
            </div>
          </div>

          <div v-if="sending" class="msg-row assistant">
            <div class="msg-avatar assistant">{{ assistantInitial }}</div>
            <div class="msg-body">
              <div class="msg-bubble loading-bubble">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span style="margin-left:6px">思考中...</span>
              </div>
            </div>
          </div>

          <el-empty v-if="!messages.length && !sending" description="开始你的第一次对话" :image-size="80" style="margin:auto" />
        </div>

        <div class="chat-input-wrap">
          <el-input v-model="input" type="textarea" :rows="2" resize="none"
            placeholder="输入问题，如：再热器壁温偏高如何调整？"
            @keydown.enter.exact.prevent="send" />
          <div class="input-actions">
            <span class="input-hint">Enter 发送，Shift+Enter 换行</span>
            <el-button type="primary" size="small" :loading="sending" :disabled="!input.trim() || !currentSessionId" @click="send">
              发送
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 右侧：召回统计 -->
      <el-card shadow="never" class="chat-right" body-style="padding:0;height:100%;overflow:auto">
        <div class="right-section">
          <div class="right-title">助手配置</div>
          <div class="right-row"><span>LLM 模型</span><span class="val">{{ currentAssistant?.llmModel || 'qwen2.5-14b' }}</span></div>
          <div class="right-row"><span>温度</span><span class="val">{{ currentAssistant?.temperature ?? 0.3 }}</span></div>
          <div class="right-row"><span>top_p</span><span class="val">{{ currentAssistant?.topP ?? 0.9 }}</span></div>
          <div class="right-row"><span>最大 token</span><span class="val">{{ currentAssistant?.maxTokens ?? 2048 }}</span></div>
          <div class="right-row" style="align-items:flex-start">
            <span style="margin-top:2px">关联知识库</span>
          </div>
          <div class="kb-list">
            <el-tag v-for="kb in (currentAssistant?.datasets || [])" :key="kb" size="small" type="info" effect="plain">
              {{ kb }}
            </el-tag>
            <span v-if="!currentAssistant?.datasets?.length" style="font-size:12px;color:var(--text-4)">-</span>
          </div>
        </div>

        <div class="right-section">
          <div class="right-title">本次会话召回统计</div>
          <div class="stat-grid">
            <div class="stat-box">
              <div class="stat-box-num">{{ totalChunks }}</div>
              <div class="stat-box-label">召回 chunk 数</div>
            </div>
            <div class="stat-box">
              <div class="stat-box-num">{{ avgSimilarity }}</div>
              <div class="stat-box-label">平均相似度</div>
            </div>
          </div>
          <div class="right-row"><span>use_kg</span>
            <el-tag size="small" type="success">{{ currentAssistant?.useKg ? 'true' : 'false' }}</el-tag>
          </div>
          <div class="right-row"><span>top_k</span><span class="val">{{ currentAssistant?.topK ?? 8 }}</span></div>
        </div>

        <div class="right-section">
          <div class="right-title">相关问题推荐</div>
          <div v-for="q in relatedQuestions" :key="q" class="rel-q" @click="useRelatedQuestion(q)">{{ q }}</div>
        </div>
      </el-card>
    </div>

    <!-- 创建助手弹窗 -->
    <el-dialog v-model="showCreateAssistant" title="创建对话助手" width="560px">
      <el-form :model="newAssistant" label-width="100px" size="default">
        <el-form-item label="助手名称" required>
          <el-input v-model="newAssistant.name" placeholder="如：运行规程问答助手" />
        </el-form-item>
        <el-form-item label="关联知识库" required>
          <el-select v-model="newAssistant.datasetIds" multiple filterable placeholder="选择知识库" style="width:100%">
            <el-option v-for="ds in availableDatasets" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="LLM 模型">
          <el-input v-model="newAssistant.llmModel" placeholder="如：qwen2.5-14b@ModelFactory" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="相似度阈值">
              <el-input-number v-model="newAssistant.similarityThreshold" :min="0" :max="1" :step="0.05" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="TopK">
              <el-input-number v-model="newAssistant.topK" :min="1" :max="50" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="提示词">
          <el-input v-model="newAssistant.prompt" type="textarea" :rows="3" placeholder="系统提示词（可选），如：你是发电运行知识助手，请基于知识库内容回答。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateAssistant = false">取消</el-button>
        <el-button type="primary" @click="onCreateAssistant" :loading="creating">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { chatApi, datasetApi } from '@/api'

interface Assistant {
  id: string
  name: string
  description?: string
  datasets?: string[]
  llmModel?: string
  temperature?: number
  topP?: number
  topK?: number
  maxTokens?: number
  useKg?: boolean
  prompt?: string
}

interface Session {
  id: string
  name?: string
  preview?: string
  updatedAt?: string
  time?: string
}

interface ChatChunk {
  chunkId?: string
  content: string
  similarity: number
  documentId?: string
  documentName?: string
  docName?: string
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  reference?: { chunks: ChatChunk[] }
}

// ===== 状态 =====
const assistants = ref<Assistant[]>([])
const sessions = ref<Session[]>([])
const messages = ref<Message[]>([])
const currentAssistantId = ref('')
const currentSessionId = ref('')
const input = ref('')

// 创建助手
const showCreateAssistant = ref(false)
const creating = ref(false)
const availableDatasets = ref<any[]>([])
const newAssistant = ref({
  name: '', datasetIds: [] as string[], llmModel: 'qwen2.5-14b@ModelFactory',
  similarityThreshold: 0.2, topK: 10, prompt: '',
})
const sending = ref(false)
const msgListRef = ref<HTMLElement>()

const currentAssistant = computed(() =>
  assistants.value.find(a => a.id === currentAssistantId.value),
)
const assistantInitial = computed(() => {
  const n = currentAssistant.value?.name || 'AI'
  return n.substring(0, 1)
})

const totalChunks = computed(() => {
  return messages.value.reduce((sum, m) => sum + (m.reference?.chunks?.length || 0), 0)
})
const avgSimilarity = computed(() => {
  const all: number[] = []
  messages.value.forEach(m => m.reference?.chunks?.forEach(c => all.push(Number(c.similarity) || 0)))
  if (!all.length) return '-'
  return (all.reduce((s, n) => s + n, 0) / all.length).toFixed(2)
})

const relatedQuestions = ref([
  '再热器壁温偏高时应如何调整烟气挡板？',
  '过热器管材 T91 与 TP347H 的使用温度界限是多少？',
  '滑参数停机过程中主蒸汽温度控制要点是什么？',
])

// ===== 加载 =====
async function loadAssistants() {
  try {
    const res: any = await chatApi.listAssistants()
    // 兼容多种返回格式：直接数组 / {list:[...]} / {chats:[...]} / {data:[...]}
    let list: any[] = []
    if (Array.isArray(res)) list = res
    else if (res?.list) list = res.list
    else if (res?.chats) list = res.chats
    else if (res?.data && Array.isArray(res.data)) list = res.data
    assistants.value = list.map((a: any) => ({
      id: a.id || a.chatId || '',
      name: a.name || a.title || '未命名助手',
      description: a.description || '',
      datasets: a.datasetIds || a.dataset_ids || [],
      llmModel: a.llmModel || a.llm_id || a.llmId || '',
      temperature: a.temperature ?? 0.3,
      topK: a.topK ?? a.top_k ?? 10,
      useKg: a.useKg ?? false,
    }))
  } catch {
    assistants.value = []
  }
  if (assistants.value.length && !currentAssistantId.value) {
    currentAssistantId.value = assistants.value[0].id
    await loadSessions()
  }
}

async function loadSessions() {
  if (!currentAssistantId.value) return
  try {
    const res: any = await chatApi.listSessions(currentAssistantId.value)
    let list: any[] = []
    if (Array.isArray(res)) list = res
    else if (res?.sessions) list = res.sessions
    else if (res?.list) list = res.list
    else if (res?.data && Array.isArray(res.data)) list = res.data
    sessions.value = list.map((s: any) => ({
      id: s.id || s.sessionId || '',
      name: s.name || s.messages?.slice(-1)[0]?.content?.slice(0, 30) || '会话',
      preview: s.preview || s.lastMessage || s.messages?.slice(-1)[0]?.content?.slice(0, 50),
      updatedAt: s.updatedAt || s.updated_at || s.time,
      time: s.time,
    }))
  } catch {
    sessions.value = []
  }
  if (sessions.value.length && !currentSessionId.value) {
    switchSession(sessions.value[0].id)
  }
}

function onAssistantChange() {
  currentSessionId.value = ''
  messages.value = []
  sessions.value = []
  loadSessions()
}

async function createSession() {
  if (!currentAssistantId.value) return
  try {
    const res: any = await chatApi.createSession(currentAssistantId.value, '新会话 ' + new Date().toLocaleTimeString())
    const newSession: Session = {
      id: res?.id || String(Date.now()),
      name: res?.name || '新会话',
      updatedAt: new Date().toLocaleString(),
    }
    sessions.value.unshift(newSession)
    switchSession(newSession.id)
    ElMessage.success('已创建会话')
  } catch (e: any) {
    ElMessage.error('创建会话失败: ' + e.message)
  }
}

async function onDeleteAssistant() {
  if (!currentAssistantId.value) return
  try {
    await ElMessageBox.confirm(`确认删除助手「${currentAssistant.value?.name}」？`, '删除确认', { type: 'warning' })
    await chatApi.deleteAssistant(currentAssistantId.value)
    ElMessage.success('已删除')
    currentAssistantId.value = ''
    sessions.value = []
    messages.value = []
    await loadAssistants()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + e.message)
  }
}

async function onDeleteSession(sessionId: string) {
  try {
    await ElMessageBox.confirm('确认删除此会话？', '删除确认', { type: 'warning' })
    await chatApi.deleteSession(currentAssistantId.value, sessionId)
    ElMessage.success('已删除')
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = ''
      messages.value = []
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + e.message)
  }
}

function switchSession(sessionId: string) {
  currentSessionId.value = sessionId
  // 切换会话时清空消息（实际项目可从后端拉历史）
  messages.value = []
}

// ===== 发送对话 =====
async function send() {
  const q = input.value.trim()
  if (!q || !currentSessionId.value || sending.value) return

  messages.value.push({ role: 'user', content: q })
  input.value = ''
  scrollBottom()
  sending.value = true

  try {
    const res = await chatApi.converse(currentAssistantId.value, {
      question: q,
      sessionId: currentSessionId.value,
    })
    messages.value.push({
      role: 'assistant',
      content: res?.answer || '(无回复)',
      reference: res?.reference,
    })
  } catch (e: any) {
    messages.value.push({
      role: 'assistant',
      content: '对话失败: ' + e.message,
    })
  } finally {
    sending.value = false
    scrollBottom()
  }
}

async function regenerate(idx: number) {
  const target = messages.value[idx]
  if (!target || target.role !== 'assistant') return
  // 找到上一条 user 消息
  let prevUser: Message | undefined
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') { prevUser = messages.value[i]; break }
  }
  if (!prevUser) return
  messages.value.splice(idx, 1)
  input.value = prevUser.content
  // 删除原 user 消息，重新发送
  const uIdx = messages.value.indexOf(prevUser)
  if (uIdx >= 0) messages.value.splice(uIdx, 1)
  await send()
}

function copyMessage(content: string) {
  navigator.clipboard?.writeText(content).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败'),
  )
}

function useRelatedQuestion(q: string) {
  input.value = q
}

function onCollapseChange(_val: any, _idx: number) {
  // placeholder for analytics
}

// ===== 渲染辅助 =====
function renderContent(content: string): string {
  if (!content) return ''
  // 简单换行处理（实际项目可接入 markdown 渲染器）
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

function similarityType(sim: number): 'success' | 'primary' | 'info' {
  const n = Number(sim) || 0
  if (n >= 0.8) return 'success'
  if (n >= 0.6) return 'primary'
  return 'info'
}

function scrollBottom() {
  nextTick(() => {
    const el = msgListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadDatasets() {
  try {
    const resp = await datasetApi.list(1, 100)
    availableDatasets.value = resp?.datasets || []
  } catch { /* 静默 */ }
}

async function onCreateAssistant() {
  if (!newAssistant.value.name.trim()) { ElMessage.warning('请输入助手名称'); return }
  if (!newAssistant.value.datasetIds.length) { ElMessage.warning('请选择关联知识库'); return }
  creating.value = true
  try {
    await chatApi.createAssistant({
      name: newAssistant.value.name,
      datasetIds: newAssistant.value.datasetIds,
      llm: { model: newAssistant.value.llmModel, temperature: 0.3 },
      prompt: newAssistant.value.prompt ? { system: newAssistant.value.prompt } : undefined,
      similarityThreshold: newAssistant.value.similarityThreshold,
      topK: newAssistant.value.topK,
    })
    ElMessage.success('助手创建成功')
    showCreateAssistant.value = false
    newAssistant.value = { name: '', datasetIds: [], llmModel: 'qwen2.5-14b@ModelFactory', similarityThreshold: 0.2, topK: 10, prompt: '' }
    await loadAssistants()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + e.message)
  } finally { creating.value = false }
}

onMounted(() => {
  loadAssistants()
  loadDatasets()
})
</script>

<style scoped>
.chat-page { padding: 24px; }
.chat-wrap {
  display: grid;
  grid-template-columns: 260px 1fr 280px;
  gap: 16px;
  height: calc(100vh - var(--header-height, 48px) - 140px);
  min-height: 600px;
}

/* 左侧 */
.chat-left { overflow: hidden; }
.asst-block { padding: 0 4px 12px; border-bottom: 1px solid var(--border-2); margin-bottom: 12px; }
.asst-block-label { font-size: 12px; color: var(--text-3); margin-bottom: 8px; padding: 0 4px; }
.asst-meta { font-size: 11px; color: var(--text-4); margin-top: 8px; padding: 0 4px; line-height: 1.5; }
.api-tag {
  font-family: Consolas, Monaco, monospace; font-size: 11px;
  background: var(--border-2); color: var(--text-3);
  padding: 1px 6px; border-radius: 3px; margin-right: 4px;
}
.left-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 4px 8px;
}
.left-head-title { font-size: 13px; font-weight: 600; color: var(--text-2); }
.sess-list { flex: 1; overflow: auto; padding: 0 4px; }
.sess-item {
  padding: 8px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 4px;
}
.sess-item:hover { background: var(--fill-1); }
.sess-item.active { background: var(--primary-bg, #FFF1F0); }
.sess-item.active .sess-title { color: var(--primary-active, #A6000A); }
.sess-title {
  font-size: 13px; color: var(--text-1); margin-bottom: 2px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 500;
}
.sess-time { font-size: 11px; color: var(--text-4); }

/* 中间 */
.chat-mid { overflow: hidden; }
.chat-top {
  height: 44px; border-bottom: 1px solid var(--border-2);
  display: flex; align-items: center; gap: 8px; padding: 0 16px; flex-shrink: 0;
}
.chat-top-name { font-size: 14px; font-weight: 600; color: var(--text-1); margin-right: 8px; }
.msg-list {
  flex: 1; overflow: auto; padding: 20px 24px;
  display: flex; flex-direction: column; gap: 20px;
}
.msg-row { display: flex; gap: 12px; }
.msg-row.user { flex-direction: row-reverse; }
.msg-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 500; flex-shrink: 0;
}
.msg-avatar.assistant { background: var(--primary); color: #fff; }
.msg-avatar.user { background: var(--border-2); color: var(--text-2); }
.msg-body { max-width: 78%; display: flex; flex-direction: column; }
.msg-row.user .msg-body { align-items: flex-end; }
.msg-bubble {
  padding: 12px 16px; border-radius: 8px;
  font-size: 14px; line-height: 1.7; word-break: break-word;
}
.msg-row.assistant .msg-bubble {
  background: #fff; border: 1px solid var(--border-2);
  box-shadow: var(--shadow-1); border-top-left-radius: 4px;
}
.msg-row.user .msg-bubble {
  background: var(--primary); color: #fff; border-top-right-radius: 4px;
}
.loading-bubble { display: inline-flex; align-items: center; color: var(--text-3); }

.source-collapse {
  margin-top: 12px; border-top: 1px dashed var(--border-2);
}
.source-collapse :deep(.el-collapse-item__header) {
  border: none; height: 28px; line-height: 28px;
}
.sources-title { font-size: 12px; color: var(--text-3); }
.source-item {
  background: var(--fill-1); border: 1px solid var(--border-2);
  border-radius: 6px; padding: 8px 12px; margin-bottom: 8px; font-size: 12px;
}
.source-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.source-doc {
  color: var(--text-2); font-weight: 500; flex: 1; min-width: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.source-snippet {
  color: var(--text-3); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}

.msg-actions { display: flex; gap: 8px; margin-top: 8px; }

/* 输入区 */
.chat-input-wrap { border-top: 1px solid var(--border-2); padding: 12px 16px; flex-shrink: 0; }
.input-actions {
  display: flex; align-items: center; gap: 8px; margin-top: 8px;
}
.input-hint { font-size: 11px; color: var(--text-4); margin-right: auto; }

/* 右侧 */
.chat-right { overflow: auto; }
.right-section { padding: 16px; border-bottom: 1px solid var(--border-2); }
.right-title { font-size: 13px; font-weight: 600; color: var(--text-2); margin-bottom: 12px; }
.right-row {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 12px; padding: 4px 0; color: var(--text-2);
}
.right-row .val {
  color: var(--text-1); font-weight: 500;
  font-family: Consolas, Monaco, monospace; font-size: 12px;
}
.kb-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.stat-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 12px;
}
.stat-box {
  background: var(--fill-1); border-radius: 4px; padding: 8px 12px;
}
.stat-box-num { font-size: 16px; font-weight: 600; color: var(--primary-active, #A6000A); }
.stat-box-label { font-size: 11px; color: var(--text-3); margin-top: 2px; }
.rel-q {
  font-size: 12px; color: var(--text-2);
  padding: 8px 12px; background: var(--fill-1); border-radius: 4px;
  margin-bottom: 8px; cursor: pointer; line-height: 1.5;
}
.rel-q:hover { background: var(--primary-bg, #FFF1F0); color: var(--primary-active, #A6000A); }
</style>
