<template>
  <div class="dyauthor-container">
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
            v-model="statusSelection"
            placeholder="Status"
            clearable
            @change="searchFn"
            style="width: 120px"
          >
            <el-option v-for="(label, value) in statusOptions" :key="value" :label="label" :value="value"> </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="starsSelection"
            placeholder="Rate"
            clearable
            @change="searchFn"
            style="width: 120px"
          >
            <el-option v-for="v in starsOptions" :key="v" :label="v" :value="v"> </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="isFavor"
            placeholder="Favor"
            clearable
            @change="searchFn"
            style="width: 120px"
          >
            <el-option label="Y" :value="true"> </el-option>
            <el-option label="N" :value="false"> </el-option>
          </el-select>
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
        <el-form-item class="mobile-search-trigger"><el-button icon="Search" @click="searchFn">Search</el-button></el-form-item>
        <el-form-item class="mobile-filter-toggle-item" v-if="isMobile">
          <el-button text class="mobile-collapse-btn" @click="mobileFiltersExpanded = !mobileFiltersExpanded">
            <el-icon><ArrowUp v-if="mobileFiltersExpanded" /><ArrowDown v-else /></el-icon>
          </el-button>
        </el-form-item>
        <el-form-item><el-button icon="Plus" @click="showDialog()">Create</el-button></el-form-item>
      </el-form>
    </div>
    <div v-loading="isLoading" class="content-view">
      <el-table :data="tableData" :height="tableHeight" style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column v-if="!isMobile" type="selection" width="55" />
        <el-table-column v-if="!isMobile" type="index" label="#" width="60" />
        <el-table-column prop="name" label="Name" :min-width="isMobile ? 170 : 260" show-overflow-tooltip>
          <template #default="scope">
            <div class="name-cell" @click="router.push({ name: 'dyauthor-detail', params: { id: scope.row.id } })">
              <el-image :src="scope.row.avatar_src || defaultAvatar" class="avatar-img" fit="cover">
                <template #error>
                  <img :src="defaultAvatar" class="avatar-img" />
                </template>
              </el-image>
              <span class="name-text">{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="path" label="Path" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="url" label="Url" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <el-link :href="scope.row.url" type="primary" target="_blank" v-if="scope.row.url">
              {{ scope.row.url }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="rate" :min-width="isMobile ? 110 : 120" label="Rate" show-overflow-tooltip>
          <template #default="scope">
            <el-rate v-model="scope.row.rate" @change="changeRate(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="status" label="Status" :min-width="isMobile ? 110 : 100" show-overflow-tooltip>
          <template #default="scope">
            {{ statusOptions[scope.row.status] || scope.row.status }}
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="is_favor" label="Favor" min-width="80" align="center" show-overflow-tooltip>
          <template #default="scope">
            <el-icon
              :color="scope.row.is_favor ? '#f56c6c' : '#909399'"
              @click="changeFavor(scope.row)"
              style="cursor: pointer; font-size: 20px"
            >
              <StarFilled v-if="scope.row.is_favor" />
              <Star v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="is_valid" label="Valid" min-width="80" align="center" show-overflow-tooltip>
          <template #default="scope">
            <el-tag :type="scope.row.is_valid ? 'success' : 'danger'">
              {{ scope.row.is_valid ? 'Yes' : 'No' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="updatedAt" label="Updated" min-width="150" show-overflow-tooltip />
        <el-table-column
          :label="isMobile ? 'A' : 'Action'"
          :min-width="isMobile ? 40 : 120"
          :width="isMobile ? 40 : undefined"
          align="center"
          :fixed="isMobile ? false : 'right'"
        >
          <template #default="scope">
            <el-dropdown v-if="isMobile" trigger="click" placement="bottom-end">
              <el-button text class="mobile-actions-trigger">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="op in operations"
                    :key="op.flag"
                    @click="actionFn(scope.row, op.flag)"
                  >
                    <el-icon><component :is="op.icon"></component></el-icon>
                    <span style="margin-left: 6px">{{ op.label }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <template v-else>
              <span
                v-for="op in operations"
                class="list-item-operation"
                @click="actionFn(scope.row, op.flag)"
                :key="op.flag"
              >
                <el-icon><component :is="op.icon"></component></el-icon>
              </span>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div ref="paginationRef" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100, 200]"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        size="small"
      />
    </div>
    <el-drawer v-model="drawer" :title="drawerTitle">
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(v, k) in drawerContent" :label="k" :key="k">
          <el-link v-if="k === 'URL' && v && v !== '-'" :href="v" type="primary" target="_blank">
            {{ v }}
          </el-link>
          <span v-else>{{ v }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
    <el-dialog v-model="dialogVisible" :title="dialogTitle">
      <el-form :model="formData" ref="rulesForm" :rules="rules">
        <el-form-item label="URL" prop="url">
          <el-input v-model="formData.url" placeholder="Enter author URL" />
        </el-form-item>
        <el-form-item label="Valid">
          <el-switch v-model="formData.is_valid" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleClose">Cancel</el-button>
        <el-button type="primary" :loading="loadingSave" @click="submitData">Submit</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="crawlDialogVisible" title="Crawl Authors" width="600px">
      <el-form :model="crawlForm" ref="crawlFormRef" label-width="140px">
        <el-form-item label="Multipage">
          <el-switch v-model="crawlForm.multipage" />
        </el-form-item>
        <el-form-item label="Page Count">
          <el-input-number v-model="crawlForm.page_count" :min="0" placeholder="0 = off" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Crawl Workers">
          <el-input-number v-model="crawlForm.num_crawl_workers" :min="0" placeholder="0 = auto" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Save Workers">
          <el-input-number v-model="crawlForm.num_save_workers" :min="1" :max="10" :default-value="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Browser Size">
          <el-input-number v-model="crawlForm.browser_width" :min="800" :max="3840" :default-value="1920" style="width: 48%" />
          <el-input-number v-model="crawlForm.browser_height" :min="600" :max="2160" :default-value="1080" style="width: 48%; margin-left: 4%" />
        </el-form-item>
        <el-form-item label="Headless">
          <el-switch v-model="crawlForm.headless" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCrawlClose">Cancel</el-button>
        <el-button type="primary" :loading="crawlLoading" @click="submitCrawl">Submit</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteUpdate } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, InfoFilled, Edit, Delete, Download, Plus, MoreFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { ajax } from '@/utils/request'
import { timeOption } from '@/config'

const defaultAvatar = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="20" height="20"%3E%3Crect width="20" height="20" fill="%23ddd"/%3E%3C/svg%3E'

const route = useRoute()
const router = useRouter()

const searchForm = ref({ limit: 20 })
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchInput = ref('')
const isLoading = ref(false)

const statusSelection = ref(null)
const starsSelection = ref(null)
const isFavor = ref(null)

const searchFormRef = ref(null)
const paginationRef = ref(null)

const drawer = ref(false)
const drawerContent = ref({})
const drawerTitle = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('Create Author')
const formData = ref({ url: '', is_valid: true })
const loadingSave = ref(false)
const rulesForm = ref(null)

const crawlDialogVisible = ref(false)
const crawlForm = ref({
  multipage: false,
  page_count: 0,
  num_crawl_workers: 0,
  num_save_workers: 3,
  browser_width: 1920,
  browser_height: 1080,
  headless: true,
})
const crawlLoading = ref(false)
const crawlFormRef = ref(null)
const pendingCrawlIds = ref([])
const isBatchCrawl = ref(false)

const batchSelections = ref([])
const batchOptions = ref([
  { label: 'Batch Delete', value: 'delete' },
  { label: 'Batch Crawl', value: 'crawl' },
  { label: 'Advanced Crawl', value: 'advanced_crawl' },
])

const rules = {
  url: [{ required: true, message: 'URL is required', trigger: 'blur' }],
}

const starsOptions = ref([0, 1, 2, 3, 4, 5])

const statusOptions = {
  ready: 'Ready',
  waiting: 'Waiting',
  running: 'Running',
  error: 'Error',
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

const changeRate = (data) => {
  ajax.patch(`/dy/author/${data.id}/`, { rate: data.rate }).catch((err) => {
    ElMessage.error('Failed to update rate')
  })
}

const changeFavor = (data) => {
  data.is_favor = !data.is_favor
  ajax.patch(`/dy/author/${data.id}/`, { is_favor: data.is_favor }).catch((err) => {
    data.is_favor = !data.is_favor
    ElMessage.error('Failed to update favor')
  })
}

const operations = ref([
  { label: 'Detail', flag: 'detail', icon: InfoFilled },
  { label: 'Edit', flag: 'edit', icon: Edit },
  { label: 'Delete', flag: 'delete', icon: Delete },
  { label: 'Crawl', flag: 'crawl', icon: Download },
  //{ label: 'Advanced Crawl', flag: 'advanced_crawl', icon: Download },
])

const showDrawerFn = (item, flag) => {
  drawer.value = true
  drawerContent.value = {
    Name: item.name,
    ID: item.id,
    'Unique Id': item.unique_id,
    Path: item.path,
    URL: item.url || '-',
    Desc: item.desc || '-',
    Status: statusOptions[item.status] || item.status,
    Rate: item.rate || 0,
    Favor: item.is_favor ? 'Yes' : 'No',
    Valid: item.is_valid ? 'Yes' : 'No',
    'Created At': item.createdAt || '-',
    'Updated At': item.updatedAt || '-',
  }
  drawerTitle.value = `${flag} of ${item.name}`
}

const showDialog = (data) => {
  if (data) {
    dialogTitle.value = `Edit Author ${data.name}`
    formData.value = {
      url: data.url || '',
      is_valid: data.is_valid !== undefined ? data.is_valid : true,
    }
    formData.value.id = data.id
  } else {
    dialogTitle.value = 'Create Author'
    formData.value = { url: '', is_valid: true }
    delete formData.value.id
  }
  dialogVisible.value = true
}

const handleClose = () => {
  dialogVisible.value = false
  formData.value = { url: '', is_valid: true }
  delete formData.value.id
  if (rulesForm.value) {
    rulesForm.value.clearValidate()
  }
}

const submitData = () => {
  if (!rulesForm.value) return
  rulesForm.value.validate((valid) => {
    if (valid) {
      loadingSave.value = true
      let params = {
        url: formData.value.url.split('?')[0],
        is_valid: formData.value.is_valid,
      }
      let api = null
      let msg = 'created'
      
      if (formData.value.id) {
        api = ajax.patch(`/dy/author/${formData.value.id}/`, params)
        msg = 'updated'
      } else {
        api = ajax.post('/dy/author/', params)
        msg = 'created'
      }
      
      api
        .then((res) => {
          ElMessage.success(`Author ${msg} successfully`)
          loadingSave.value = false
          handleClose()
          getData()
        })
        .catch((err) => {
          const errorMsg = err.response?.data?.detail || err.response?.data?.message || 'Failed to save author'
          ElMessage.error(errorMsg)
          loadingSave.value = false
        })
    }
  })
}

const actionFn = (data, flag) => {
  if (flag == 'detail') {
    showDrawerFn(data, 'Detail')
  } else if (flag == 'edit') {
    showDialog(data)
  } else if (flag == 'delete') {
    ElMessageBox.confirm(
      'Delete ' + "<span style='font-weight: bold;color:red;'>" + data.name + '</span> ?',
      'Warning',
      {
        closeOnClickModal: false,
        dangerouslyUseHTMLString: true,
        type: 'warning',
      },
    ).then(() => {
      ajax
        .delete(`/dy/author/${data.id}/`)
        .then(() => {
          ElMessage.success(`${data.name} deleted`)
          getData()
        })
        .catch((err) => {
          ElMessage.error('Failed to delete')
        })
    }).catch(() => {})
  } else if (flag == 'crawl') {
    // Normal crawl for single author (no parameters)
    ajax.post(`/dy/author/crawl/`, { ids: [data.id] }).then((res) => {
      ElMessage.success(res.data.msg || 'Crawl started')
      getData()
    }).catch((err) => {
      ElMessage.error('Failed to start crawl')
    })
  } else if (flag == 'advanced_crawl') {
    // Show crawl dialog for single author advanced crawl
    pendingCrawlIds.value = [data.id]
    isBatchCrawl.value = false
    crawlForm.value = {
      multipage: false,
      page_count: 0,
      num_crawl_workers: 0,
      num_save_workers: 3,
      browser_width: 1920,
      browser_height: 1080,
      headless: true,
    }
    crawlDialogVisible.value = true
  }
}

const handleSelectionChange = (selection) => {
  batchSelections.value = selection.map((x) => x.id)
}

const onBatchOperation = (val) => {
  if (!val) return
  if (batchSelections.value.length === 0) {
    ElMessage.warning('Please select at least one author')
    return
  }
  
  if (val === 'delete') {
    const selectedData = tableData.value.filter(row => batchSelections.value.includes(row.id))
    const names = selectedData.map(row => row.name).join(', ')
    ElMessageBox.confirm(
      `Delete <span style='font-weight: bold;color:red;'>${batchSelections.value.length}</span> author(s)?<br/>${names}`,
      'Warning',
      {
        closeOnClickModal: false,
        dangerouslyUseHTMLString: true,
        type: 'warning',
      },
    ).then(() => {
      const deletePromises = batchSelections.value.map(id => ajax.delete(`/dy/author/${id}/`))
      Promise.all(deletePromises)
        .then(() => {
          ElMessage.success(`${batchSelections.value.length} author(s) deleted`)
          batchSelections.value = []
          getData()
        })
        .catch((err) => {
          ElMessage.error('Failed to delete some authors')
          getData()
        })
    }).catch(() => {})
  } else if (val === 'crawl') {
    // Normal batch crawl (no parameters)
    ajax.post(`/dy/author/crawl/`, { ids: batchSelections.value }).then((res) => {
      ElMessage.success(res.data.msg || `Crawl started for ${batchSelections.value.length} author(s)`)
      batchSelections.value = []
      getData()
    }).catch((err) => {
      const errorMsg = err.response?.data?.detail || err.response?.data?.message || 'Failed to start crawl'
      ElMessage.error(errorMsg)
    })
  } else if (val === 'advanced_crawl') {
    // Show crawl dialog for advanced batch crawl
    pendingCrawlIds.value = [...batchSelections.value]
    isBatchCrawl.value = true
    crawlForm.value = {
      multipage: false,
      page_count: 0,
      num_crawl_workers: 0,
      num_save_workers: 3,
      browser_width: 1920,
      browser_height: 1080,
      headless: true,
    }
    crawlDialogVisible.value = true
  }
}

const searchFn = () => {
  let query = {
    ...searchForm.value,
    search: searchInput.value,
    status: statusSelection.value,
    rate: starsSelection.value,
    is_favor: isFavor.value,
  }
  // Remove page parameter when performing new search
  delete query.page
  if (!query.search) delete query.search
  if (statusSelection.value === null) delete query.status
  if (starsSelection.value === null) delete query.rate
  if (isFavor.value === null) delete query.is_favor
  
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
  data.createdAt = new Date(data.created_at).toLocaleString('zh-CN', timeOption)
  data.updatedAt = new Date(data.updated_at).toLocaleString('zh-CN', timeOption)
  if (data.updated_at)
    data.updatedAt = new Date(data.updated_at).toLocaleString('zh-CN', timeOption)
  else data.updatedAt = 'Never'
  // Status is already in the correct format from backend (ready, waiting, running, error)
}

const getData = async () => {
  let params = { ...searchForm.value }
  isLoading.value = true
  try {
    const res = await ajax.get('/dy/author/', params)
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
    ElMessage.error('Failed to load authors')
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

const loadDataFromRoute = () => {
  searchForm.value = { ...route.query }
  searchInput.value = searchForm.value.search || ''
  pageSize.value = searchForm.value.limit ? Number(searchForm.value.limit) : 20
  currentPage.value = searchForm.value.page ? Number(searchForm.value.page) : 1
  searchForm.value.limit = pageSize.value
  
  if (searchForm.value.status) statusSelection.value = searchForm.value.status
  else statusSelection.value = null
  if (searchForm.value.rate) starsSelection.value = Number(searchForm.value.rate)
  else starsSelection.value = null
  if (searchForm.value.is_favor !== undefined)
    isFavor.value = searchForm.value.is_favor === 'true' || searchForm.value.is_favor === true
  else isFavor.value = null
  
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
  
  if (to.query.status) statusSelection.value = to.query.status
  else statusSelection.value = null
  if (to.query.rate) starsSelection.value = Number(to.query.rate)
  else starsSelection.value = null
  if (to.query.is_favor !== undefined)
    isFavor.value = to.query.is_favor === 'true' || to.query.is_favor === true
  else isFavor.value = null
  
  getData()
})

onMounted(() => {
  updateMobileState()
  loadDataFromRoute()
  nextTick(() => {
    getTableHeight()
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

const handleCrawlClose = () => {
  crawlDialogVisible.value = false
  pendingCrawlIds.value = []
  crawlForm.value = {
    multipage: false,
    page_count: 0,
    num_crawl_workers: 0,
    num_save_workers: 3,
    browser_width: 1920,
    browser_height: 1080,
    headless: true,
  }
  if (crawlFormRef.value) {
    crawlFormRef.value.clearValidate()
  }
}

const submitCrawl = () => {
  crawlLoading.value = true
  
  const payload = {
    ids: pendingCrawlIds.value,
    multipage: crawlForm.value.multipage || false,
    page_count: crawlForm.value.page_count || 0,
    num_crawl_workers: crawlForm.value.num_crawl_workers || 0,
    num_save_workers: crawlForm.value.num_save_workers || 3,
    browser_size: [crawlForm.value.browser_width || 1920, crawlForm.value.browser_height || 1080],
    headless: crawlForm.value.headless !== false,
  }
  
  ajax.post(`/dy/author/crawl/`, payload)
    .then((res) => {
      const count = isBatchCrawl.value ? pendingCrawlIds.value.length : 1
      ElMessage.success(res.data.msg || `Crawl started for ${count} author(s)`)
      crawlLoading.value = false
      handleCrawlClose()
      if (isBatchCrawl.value) {
        batchSelections.value = []
      }
      getData()
    })
    .catch((err) => {
      const errorMsg = err.response?.data?.detail || err.response?.data?.message || 'Failed to start crawl'
      ElMessage.error(errorMsg)
      crawlLoading.value = false
    })
}
</script>

<style scoped>
.name-cell {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.avatar-img {
  width: 25px;
  height: 25px;
  margin-right: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.list-item-operation {
  margin-right: 10px;
  cursor: pointer;
}
</style>