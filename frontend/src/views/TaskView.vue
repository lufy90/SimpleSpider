<template>
  <div class="task-container">
    <div
      class="search-form mobile-collapsible"
      :class="{ 'mobile-expanded': mobileFiltersExpanded }"
      ref="searchFormRef"
    >
      <el-form :model="searchForm" inline>
        <el-form-item>
          <el-input
            v-model="searchInput"
            placeholder="Search"
            prefix-icon="Search"
            @keydown.enter.prevent="searchFn"
            style="width: 240px"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="taskTypeSelection"
            placeholder="Task Type"
            clearable
            @change="searchFn"
            style="width: 150px"
          >
            <el-option
              v-for="(v, k) in taskTypeOptions"
              :key="k"
              :label="v"
              :value="k"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="statusSelection"
            placeholder="Status"
            clearable
            @change="searchFn"
            style="width: 120px"
          >
            <el-option
              v-for="(v, k) in statusOptions"
              :key="k"
              :label="v"
              :value="k"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item class="mobile-search-trigger"><el-button icon="Search" @click="searchFn">Search</el-button></el-form-item>
        <el-form-item class="mobile-filter-toggle-item" v-if="isMobile">
          <el-button text class="mobile-collapse-btn" @click="mobileFiltersExpanded = !mobileFiltersExpanded">
            <el-icon><ArrowUp v-if="mobileFiltersExpanded" /><ArrowDown v-else /></el-icon>
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button icon="Refresh" @click="getData">Refresh</el-button>
        </el-form-item>
        <el-form-item>
          <el-select
            placeholder="Batch"
            clearable
            @change="onBatchOperation"
            style="width: 120px"
            :disabled="batchSelections.length == 0"
          >
            <el-option
              v-for="item in batchOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item><el-button icon="Plus" @click="showDialog()">Create</el-button></el-form-item>
        <el-form-item><el-button icon="Download" @click="showCrawlAllDialog()">Crawl All</el-button></el-form-item>
      </el-form>
    </div>
    <div v-loading="isLoading" class="content-view">
      <el-table 
        :data="tableData" 
        :height="tableHeight" 
        style="width: 100%" 
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
      >
        <el-table-column v-if="!isMobile" type="selection" width="55" />
        <el-table-column v-if="!isMobile" type="index" label="#" width="60" />
        <el-table-column prop="task_type" label="Task Type" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="Status" min-width="120" show-overflow-tooltip>
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.originalStatus || getOriginalStatus(scope.row.status))">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="priority" label="Priority" min-width="100" align="center" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="progress" label="Progress" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <el-progress
              :percentage="scope.row.progress"
              :status="(scope.row.originalStatus === 'failed' || scope.row.status === 'Failed') ? 'exception' : undefined"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" label="Items" min-width="120" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.processed_items }} / {{ scope.row.total_items }}
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="worker_id" label="Worker ID" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="created_at" label="Created" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="started_at" label="Started" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="completed_at" label="Completed" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="error_message" label="Error" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.error_message" style="color: #f56c6c">
              {{ scope.row.error_message }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          :label="isMobile ? 'A' : 'Actions'"
          :min-width="isMobile ? 40 : 100"
          :width="isMobile ? 40 : undefined"
          :fixed="isMobile ? false : 'right'"
          align="center"
        >
          <template #default="scope">
            <el-dropdown v-if="isMobile" trigger="click" placement="bottom-end">
              <el-button text class="mobile-actions-trigger">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="copyTask(scope.row)">
                    <el-icon><DocumentCopy /></el-icon>
                    <span style="margin-left: 6px">Copy</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="onTaskAction(scope.row, 'delete')">
                    <el-icon><Delete /></el-icon>
                    <span style="margin-left: 6px">Delete</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <template v-else>
              <el-button
                type="primary"
                link
                size="small"
                icon="DocumentCopy"
                @click.stop="copyTask(scope.row)"
                title="Copy task"
              />
              <el-button
                type="danger"
                link
                size="small"
                icon="Delete"
                @click.stop="deleteTask(scope.row)"
              />
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div ref="paginationRef" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        size="small"
      />
    </div>
    <el-dialog v-model="dialogVisible" :title="dialogTitle">
      <el-form :model="formData" ref="rulesForm" :rules="rules">
        <el-form-item label="Task Type" prop="task_type">
          <el-select v-model="formData.task_type" placeholder="Select task type" @change="onTaskTypeChange">
            <el-option
              v-for="(label, value) in taskTypeOptions"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Priority" prop="priority">
          <el-input-number v-model="formData.priority" :min="1" :max="10" />
        </el-form-item>
        <el-form-item v-if="formData.task_type === 'crawl_authors'" label="Author IDs" prop="author_ids">
          <el-input
            v-model="formData.author_ids"
            type="textarea"
            :rows="3"
            placeholder="Enter author IDs separated by comma or newline"
          />
        </el-form-item>
        <template v-if="formData.task_type === 'crawl_authors'">
          <el-form-item label="Page Count">
            <el-input-number v-model="formData.page_count" :min="0" placeholder="0 = off" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item v-if="formData.task_type === 'download_videos'" label="Video IDs" prop="video_ids">
          <el-input
            v-model="formData.video_ids"
            type="textarea"
            :rows="3"
            placeholder="Enter video IDs separated by comma or newline"
          />
        </el-form-item>
        <el-form-item v-if="formData.task_type === 'crawl_by_url'" label="URL" prop="url">
          <el-input v-model="formData.url" placeholder="Enter URL to crawl" />
        </el-form-item>
        <template v-if="formData.task_type === 'crawl_by_url'">
          <el-divider />
          <el-form-item label="Multipage">
            <el-switch v-model="formData.multipage" />
          </el-form-item>
          <el-form-item label="Page Count">
            <el-input-number v-model="formData.page_count" :min="0" placeholder="0 = off" style="width: 100%" />
          </el-form-item>
          <el-form-item label="Save Workers">
            <el-input-number v-model="formData.num_save_workers" :min="1" :max="10" :default-value="3" style="width: 100%" />
          </el-form-item>
          <el-form-item label="Browser Size">
            <el-input-number v-model="formData.browser_width" :min="800" :max="3840" :default-value="1920" style="width: 48%" />
            <el-input-number v-model="formData.browser_height" :min="600" :max="2160" :default-value="1080" style="width: 48%; margin-left: 4%" />
          </el-form-item>
          <el-form-item label="Headless">
            <el-switch v-model="formData.headless" />
          </el-form-item>
        </template>
        <el-form-item v-if="formData.task_type === 'update_validity'" label="Video IDs" prop="video_ids">
          <el-input
            v-model="formData.video_ids"
            type="textarea"
            :rows="3"
            placeholder="Enter video IDs separated by comma or newline"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleClose">Cancel</el-button>
        <el-button type="primary" :loading="loadingSave" @click="submitData">Submit</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="crawlAllDialogVisible" title="Crawl All Authors" width="600px">
      <el-form :model="crawlAllForm" ref="crawlAllFormRef" label-width="140px">
        <el-form-item label="Min Rate">
          <el-input-number v-model="crawlAllForm.rate__gte" :min="0" :max="10" placeholder="Minimum rate" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="Max Rate">
          <el-input-number v-model="crawlAllForm.rate__lte" :min="0" :max="10" placeholder="Maximum rate (optional)" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="crawlAllForm.status" placeholder="Select status" clearable style="width: 100%">
            <el-option label="Ready" value="ready" />
            <el-option label="Waiting" value="waiting" />
            <el-option label="Running" value="running" />
            <el-option label="Error" value="error" />
          </el-select>
        </el-form-item>
        <el-form-item label="Is Valid">
          <el-select v-model="crawlAllForm.is_valid" placeholder="Select" clearable style="width: 100%">
            <el-option label="True" :value="true" />
            <el-option label="False" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="Is Favor">
          <el-select v-model="crawlAllForm.is_favor" placeholder="Select" clearable style="width: 100%">
            <el-option label="True" :value="true" />
            <el-option label="False" :value="false" />
          </el-select>
        </el-form-item>
        <el-divider />
        <el-form-item label="Multipage">
          <el-switch v-model="crawlAllForm.multipage" />
        </el-form-item>
        <el-form-item label="Page Count">
          <el-input-number v-model="crawlAllForm.page_count" :min="0" placeholder="0 = off" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Crawl Workers">
          <el-input-number v-model="crawlAllForm.num_crawl_workers" :min="0" placeholder="0 = auto" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Save Workers">
          <el-input-number v-model="crawlAllForm.num_save_workers" :min="1" :max="10" :default-value="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Browser Size">
          <el-input-number v-model="crawlAllForm.browser_width" :min="800" :max="3840" :default-value="1920" style="width: 48%" />
          <el-input-number v-model="crawlAllForm.browser_height" :min="600" :max="2160" :default-value="1080" style="width: 48%; margin-left: 4%" />
        </el-form-item>
        <el-form-item label="Headless">
          <el-switch v-model="crawlAllForm.headless" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCrawlAllClose">Cancel</el-button>
        <el-button type="primary" :loading="crawlAllLoading" @click="submitCrawlAll">Submit</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="drawerVisible" title="Task Details" :size="600" direction="rtl">
      <div v-if="selectedTask" style="padding: 20px">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">{{ selectedTask.id }}</el-descriptions-item>
          <el-descriptions-item label="Task Type">
            {{ taskTypeOptions[selectedTask.originalTaskType] || selectedTask.originalTaskType }}
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag :type="getStatusType(selectedTask.originalStatus)">
              {{ statusOptions[selectedTask.originalStatus] || selectedTask.originalStatus }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Priority">{{ selectedTask.priority }}</el-descriptions-item>
          <el-descriptions-item label="Progress">
            <el-progress
              :percentage="selectedTask.progress"
              :status="(selectedTask.originalStatus === 'failed' || selectedTask.status === 'Failed') ? 'exception' : undefined"
            />
          </el-descriptions-item>
          <el-descriptions-item label="Items">
            {{ selectedTask.processed_items }} / {{ selectedTask.total_items }}
          </el-descriptions-item>
          <el-descriptions-item label="Error Items">{{ selectedTask.error_items || 0 }}</el-descriptions-item>
          <el-descriptions-item label="Worker ID">{{ selectedTask.worker_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Created At">{{ selectedTask.created_at }}</el-descriptions-item>
          <el-descriptions-item label="Started At">{{ selectedTask.started_at || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Completed At">{{ selectedTask.completed_at || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Parameters" v-if="selectedTask.parameters">
            <pre style="max-height: 200px; overflow-y: auto; background: #f5f5f5; padding: 10px; border-radius: 4px;">{{ JSON.stringify(selectedTask.parameters, null, 2) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="Result" v-if="selectedTask.result && Object.keys(selectedTask.result).length > 0">
            <pre style="max-height: 200px; overflow-y: auto; background: #f5f5f5; padding: 10px; border-radius: 4px;">{{ JSON.stringify(selectedTask.result, null, 2) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="Error Message" v-if="selectedTask.error_message">
            <span style="color: #f56c6c">{{ selectedTask.error_message }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteUpdate } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, DocumentCopy, MoreFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ajax } from '@/utils/request'
import { timeOption } from '@/config'

const route = useRoute()
const router = useRouter()

const searchForm = ref({ limit: 20 })
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchInput = ref('')
const isLoading = ref(false)

const taskTypeSelection = ref(null)
const statusSelection = ref(null)

const searchFormRef = ref(null)
const paginationRef = ref(null)

const dialogVisible = ref(false)
const dialogTitle = ref('Create Task')
const formData = ref({ 
  task_type: '', 
  priority: 5, 
  author_ids: '', 
  video_ids: '', 
  url: '',
  multipage: false,
  num_save_workers: 3,
  browser_width: 1920,
  browser_height: 1080,
  headless: true,
})
const loadingSave = ref(false)
const rulesForm = ref(null)

const drawerVisible = ref(false)
const selectedTask = ref(null)

const crawlAllDialogVisible = ref(false)
const crawlAllForm = ref({
  rate__gte: 3,
  rate__lte: null,
  status: null,
  is_valid: true,
  is_favor: null,
  multipage: false,
  page_count: 0,
  num_crawl_workers: 0,
  num_save_workers: 3,
  browser_width: 1920,
  browser_height: 1080,
  headless: true,
})
const crawlAllLoading = ref(false)
const crawlAllFormRef = ref(null)

const batchSelections = ref([])
const batchOptions = ref([
  { label: 'Batch Delete', value: 'delete' },
])

const rules = {
  task_type: [{ required: true, message: 'Task type is required', trigger: 'change' }],
}

const taskTypeOptions = {
  crawl_authors: 'Crawl Authors',
  download_videos: 'Download Videos',
  crawl_by_url: 'Crawl By URL',
  update_validity: 'Update Video Validity',
}

const statusOptions = {
  pending: 'Pending',
  running: 'Running',
  completed: 'Completed',
  failed: 'Failed',
  cancelled: 'Cancelled',
}

const tableHeight = ref(500)
const isMobile = ref(false)
const mobileFiltersExpanded = ref(false)

const updateMobileState = () => {
  isMobile.value = window.innerWidth <= 900
  if (!isMobile.value) {
    mobileFiltersExpanded.value = true
  }
}

const getOriginalStatus = (displayStatus) => {
  for (const [key, value] of Object.entries(statusOptions)) {
    if (value === displayStatus) {
      return key
    }
  }
  return displayStatus
}

const getStatusType = (status) => {
  const statusMap = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
    cancelled: 'info',
  }
  return statusMap[status] || 'info'
}

const getTableHeight = () => {
  const headerHeight = 110
  const mobileBottomGap = isMobile.value ? 20 : 0
  if (searchFormRef.value && paginationRef.value) {
    tableHeight.value =
      window.innerHeight -
      searchFormRef.value.offsetHeight -
      paginationRef.value.offsetHeight -
      headerHeight -
      mobileBottomGap +
      'px'
  }
}

const searchFn = () => {
  let query = {
    ...searchForm.value,
    search: searchInput.value,
    task_type: taskTypeSelection.value,
    status: statusSelection.value,
  }
  if (!query.search) delete query.search
  if (taskTypeSelection.value === null) delete query.task_type
  if (statusSelection.value === null) delete query.status
  
  router.push({
    ...route,
    query: query,
  })
}

const handleSizeChange = (x) => {
  router.push({ ...route, query: { ...searchForm.value, limit: x, page: 1 } })
}

const handleCurrentChange = (x) => {
  router.push({ ...route, query: { ...searchForm.value, page: x } })
}

const dataTransfer = (data) => {
  if (data.created_at)
    data.created_at = new Date(data.created_at).toLocaleString('zh-CN', timeOption)
  if (data.started_at)
    data.started_at = new Date(data.started_at).toLocaleString('zh-CN', timeOption)
  if (data.completed_at)
    data.completed_at = new Date(data.completed_at).toLocaleString('zh-CN', timeOption)
  
  data.originalStatus = data.status
  data.originalTaskType = data.task_type
  data.task_type = taskTypeOptions[data.task_type] || data.task_type
  data.status = statusOptions[data.status] || data.status
}

const getData = async () => {
  let params = { ...searchForm.value }
  isLoading.value = true
  try {
    const res = await ajax.get('/dy/task/', params)
    let data = res.data.results || res.data
    total.value = res.data.count || data.length
    
    if (Array.isArray(data)) {
      data.forEach((x) => {
        dataTransfer(x)
      })
      tableData.value = data
    } else {
      tableData.value = []
    }
  } catch (error) {
    ElMessage.error('Failed to load tasks')
    tableData.value = []
  } finally {
    isLoading.value = false
  }
}

const handleResize = () => {
  updateMobileState()
  nextTick(() => {
    getTableHeight()
  })
}

let refreshInterval = null

const loadDataFromRoute = () => {
  searchForm.value = { ...route.query }
  searchInput.value = searchForm.value.search || ''
  pageSize.value = searchForm.value.limit ? Number(searchForm.value.limit) : 20
  currentPage.value = searchForm.value.page ? Number(searchForm.value.page) : 1
  searchForm.value.limit = pageSize.value
  
  if (searchForm.value.task_type) taskTypeSelection.value = searchForm.value.task_type
  else taskTypeSelection.value = null
  if (searchForm.value.status) statusSelection.value = searchForm.value.status
  else statusSelection.value = null
  
  getData()
}

// Watch for route query changes
watch(() => route.query, () => {
  loadDataFromRoute()
}, { deep: true })

// Handle route updates (when navigating to same route with different params)
onBeforeRouteUpdate(async (to) => {
  // Update searchForm with new query params
  searchForm.value = { ...to.query }
  searchInput.value = to.query.search || ''
  pageSize.value = to.query.limit ? Number(to.query.limit) : 20
  currentPage.value = to.query.page ? Number(to.query.page) : 1
  searchForm.value.limit = pageSize.value
  
  if (to.query.task_type) taskTypeSelection.value = to.query.task_type
  else taskTypeSelection.value = null
  if (to.query.status) statusSelection.value = to.query.status
  else statusSelection.value = null
  
  getData()
})

onMounted(() => {
  updateMobileState()
  loadDataFromRoute()
  nextTick(() => {
    getTableHeight()
  })
  window.addEventListener('resize', handleResize)
  
  // Auto refresh every 5 seconds
  refreshInterval = setInterval(() => {
    getData()
  }, 5000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
  window.removeEventListener('resize', handleResize)
})

const showDialog = () => {
  dialogTitle.value = 'Create Task'
  formData.value = { 
    task_type: '', 
    priority: 5, 
    author_ids: '', 
    video_ids: '', 
    url: '',
    page_count: 0,
    multipage: false,
    num_save_workers: 3,
    browser_width: 1920,
    browser_height: 1080,
    headless: true,
  }
  dialogVisible.value = true
}

const handleClose = () => {
  dialogVisible.value = false
  formData.value = { 
    task_type: '', 
    priority: 5, 
    author_ids: '', 
    video_ids: '', 
    url: '',
    page_count: 0,
    multipage: false,
    num_save_workers: 3,
    browser_width: 1920,
    browser_height: 1080,
    headless: true,
  }
  if (rulesForm.value) {
    rulesForm.value.clearValidate()
  }
}

const onTaskTypeChange = () => {
  formData.value.author_ids = ''
  formData.value.video_ids = ''
  formData.value.url = ''
  formData.value.page_count = 0
  // Reset crawl_by_url specific fields
  formData.value.multipage = false
  formData.value.num_save_workers = 3
  formData.value.browser_width = 1920
  formData.value.browser_height = 1080
  formData.value.headless = true
}

const parseIds = (idsString) => {
  if (!idsString) return []
  return idsString
    .split(/[,\n]/)
    .map(id => id.trim())
    .filter(id => id && !isNaN(id))
    .map(id => parseInt(id))
}

const submitData = () => {
  if (!rulesForm.value) return
  rulesForm.value.validate((valid) => {
    if (valid) {
      loadingSave.value = true
      let parameters = {}
      
      if (formData.value.task_type === 'crawl_authors') {
        const authorIds = parseIds(formData.value.author_ids)
        if (authorIds.length === 0) {
          ElMessage.error('Please enter at least one author ID')
          loadingSave.value = false
          return
        }
        parameters = {
          authors: authorIds,
          page_count: formData.value.page_count || 0,
        }
      } else if (formData.value.task_type === 'download_videos' || formData.value.task_type === 'update_validity') {
        const videoIds = parseIds(formData.value.video_ids)
        if (videoIds.length === 0) {
          ElMessage.error('Please enter at least one video ID')
          loadingSave.value = false
          return
        }
        if (formData.value.task_type === 'download_videos') {
          parameters = { videos: videoIds }
        } else {
          parameters = { videos: videoIds }
        }
      } else if (formData.value.task_type === 'crawl_by_url') {
        if (!formData.value.url) {
          ElMessage.error('Please enter a URL')
          loadingSave.value = false
          return
        }
        parameters = { 
          url: formData.value.url,
          multipage: formData.value.multipage || false,
          page_count: formData.value.page_count || 0,
          num_save_workers: formData.value.num_save_workers || 3,
          browser_size: [formData.value.browser_width || 1920, formData.value.browser_height || 1080],
          headless: formData.value.headless !== false,
        }
      }
      
      const params = {
        task_type: formData.value.task_type,
        priority: formData.value.priority || 5,
        parameters: parameters,
      }
      
      ajax.post('/dy/task/', params)
        .then((res) => {
          ElMessage.success('Task created successfully')
          loadingSave.value = false
          handleClose()
          getData()
        })
        .catch((err) => {
          const errorMsg = err.response?.data?.detail || err.response?.data?.message || 'Failed to create task'
          ElMessage.error(errorMsg)
          loadingSave.value = false
        })
    }
  })
}

const handleSelectionChange = (selection) => {
  batchSelections.value = selection.map((x) => x.id)
}

const handleRowClick = (row) => {
  // Get the original task data from API
  ajax.get(`/dy/task/${row.id}/`)
    .then((res) => {
      const task = res.data
      // Format dates
      if (task.created_at)
        task.created_at = new Date(task.created_at).toLocaleString('zh-CN', timeOption)
      if (task.started_at)
        task.started_at = new Date(task.started_at).toLocaleString('zh-CN', timeOption)
      if (task.completed_at)
        task.completed_at = new Date(task.completed_at).toLocaleString('zh-CN', timeOption)
      
      task.originalStatus = task.status
      task.originalTaskType = task.task_type
      selectedTask.value = task
      drawerVisible.value = true
    })
    .catch((err) => {
      ElMessage.error('Failed to load task details')
    })
}

/**
 * Build form data from task row for copy. Excludes status, time fields (created_at, started_at, completed_at),
 * progress, processed_items, total_items, worker_id, error_message.
 */
const fillFormFromTask = (rawType, priority, params) => {
  const base = {
    task_type: rawType,
    priority: priority != null ? priority : 5,
    author_ids: '',
    video_ids: '',
    url: '',
    page_count: 0,
    multipage: false,
    num_save_workers: 3,
    browser_width: 1920,
    browser_height: 1080,
    headless: true,
  }
  if (rawType === 'crawl_authors') {
    base.author_ids = Array.isArray(params.authors) ? params.authors.join('\n') : ''
    base.page_count = params.page_count ?? 0
  } else if (rawType === 'download_videos' || rawType === 'update_validity') {
    base.video_ids = Array.isArray(params.videos) ? params.videos.join('\n') : ''
  } else if (rawType === 'crawl_by_url') {
    base.url = params.url || ''
    base.multipage = params.multipage ?? false
    base.page_count = params.page_count ?? 0
    base.num_save_workers = params.num_save_workers ?? 3
    const size = params.browser_size
    base.browser_width = Array.isArray(size) && size[0] != null ? size[0] : 1920
    base.browser_height = Array.isArray(size) && size[1] != null ? size[1] : 1080
    base.headless = params.headless !== false
  }
  formData.value = { ...base }
  dialogTitle.value = 'Copy Task'
  dialogVisible.value = true
}

const copyTask = (row) => {
  const rawType = row.originalTaskType || row.task_type
  const params = row.parameters || {}
  if (Object.keys(params).length === 0 && row.id != null) {
    ajax.get(`/dy/task/${row.id}/`).then((res) => {
      const task = res.data
      fillFormFromTask(task.task_type, task.priority, task.parameters || {})
    }).catch(() => {
      ElMessage.error('Failed to load task for copy')
    })
    return
  }
  fillFormFromTask(rawType, row.priority, params)
}

const deleteTask = (row) => {
  const taskLabel = taskTypeOptions[row.originalTaskType] || row.task_type
  ElMessageBox.confirm(
    `Delete task #${row.id} (${taskLabel})?`,
    'Confirm Delete',
    {
      closeOnClickModal: false,
      type: 'warning',
    },
  ).then(() => {
    ajax.delete(`/dy/task/${row.id}/`)
      .then(() => {
        ElMessage.success('Task deleted')
        getData()
        if (drawerVisible.value && selectedTask.value?.id === row.id) {
          drawerVisible.value = false
          selectedTask.value = null
        }
      })
      .catch(() => {
        ElMessage.error('Failed to delete task')
      })
  }).catch(() => {})
}

const onTaskAction = (row, action) => {
  if (action === 'delete') {
    deleteTask(row)
  }
}

const onBatchOperation = (val) => {
  if (!val) return
  if (batchSelections.value.length === 0) {
    ElMessage.warning('Please select at least one task')
    return
  }
  
  if (val === 'delete') {
    const selectedData = tableData.value.filter(row => batchSelections.value.includes(row.id))
    const taskTypes = selectedData.map(row => row.task_type).join(', ')
    ElMessageBox.confirm(
      `Delete <span style='font-weight: bold;color:red;'>${batchSelections.value.length}</span> task(s)?<br/>${taskTypes}`,
      'Warning',
      {
        closeOnClickModal: false,
        dangerouslyUseHTMLString: true,
        type: 'warning',
      },
    ).then(() => {
      const deletePromises = batchSelections.value.map(id => ajax.delete(`/dy/task/${id}/`))
      Promise.all(deletePromises)
        .then(() => {
          ElMessage.success(`${batchSelections.value.length} task(s) deleted`)
          batchSelections.value = []
          getData()
        })
        .catch((err) => {
          ElMessage.error('Failed to delete some tasks')
          getData()
        })
    }).catch(() => {})
  }
}

const showCrawlAllDialog = () => {
  crawlAllForm.value = {
    rate__gte: 3,
    rate__lte: null,
    status: null,
    is_valid: true,
    is_favor: null,
    multipage: false,
    page_count: 0,
    num_crawl_workers: 0,
    num_save_workers: 3,
    browser_width: 1920,
    browser_height: 1080,
    headless: true,
  }
  crawlAllDialogVisible.value = true
}

const handleCrawlAllClose = () => {
  crawlAllDialogVisible.value = false
  crawlAllForm.value = {
    rate__gte: 3,
    rate__lte: null,
    status: null,
    is_valid: true,
    is_favor: null,
    multipage: false,
    page_count: 0,
    num_crawl_workers: 0,
    num_save_workers: 3,
    browser_width: 1920,
    browser_height: 1080,
    headless: true,
  }
  if (crawlAllFormRef.value) {
    crawlAllFormRef.value.clearValidate()
  }
}

const submitCrawlAll = () => {
  crawlAllLoading.value = true
  
  // Build request payload, only include non-null filter values
  const payload = {
    rate__gte: 3, // Fixed minimum rate
    multipage: crawlAllForm.value.multipage || false,
    page_count: crawlAllForm.value.page_count || 0,
    num_crawl_workers: crawlAllForm.value.num_crawl_workers || 0,
    num_save_workers: crawlAllForm.value.num_save_workers || 3,
    browser_size: [crawlAllForm.value.browser_width || 1920, crawlAllForm.value.browser_height || 1080],
    headless: crawlAllForm.value.headless !== false,
  }
  
  // Add filter fields only if they have values
  if (crawlAllForm.value.rate__gte !== null && crawlAllForm.value.rate__gte !== undefined) {
    payload.rate__gte = crawlAllForm.value.rate__gte
  }
  if (crawlAllForm.value.rate__lte !== null && crawlAllForm.value.rate__lte !== undefined) {
    payload.rate__lte = crawlAllForm.value.rate__lte
  }
  if (crawlAllForm.value.status !== null && crawlAllForm.value.status !== undefined && crawlAllForm.value.status !== '') {
    payload.status = crawlAllForm.value.status
  }
  if (crawlAllForm.value.is_valid !== null && crawlAllForm.value.is_valid !== undefined) {
    payload.is_valid = crawlAllForm.value.is_valid
  }
  if (crawlAllForm.value.is_favor !== null && crawlAllForm.value.is_favor !== undefined) {
    payload.is_favor = crawlAllForm.value.is_favor
  }
  
  ajax.post('/dy/task/crawl-all/', payload)
    .then((res) => {
      ElMessage.success(`Crawl all task created for ${res.data.author_count} authors`)
      crawlAllLoading.value = false
      handleCrawlAllClose()
      getData()
    })
    .catch((err) => {
      const errorMsg = err.response?.data?.error || err.response?.data?.detail || err.response?.data?.message || 'Failed to create crawl all task'
      ElMessage.error(errorMsg)
      crawlAllLoading.value = false
    })
}
</script>