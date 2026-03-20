<template>
  <div class="dyvideo-container">
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
            v-model="isLike"
            placeholder="Like"
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
        <el-form-item>
          <el-button @click="toggleViewStyle">
            <el-icon><Grid v-if="viewStyle === 'list'" /><List v-else /></el-icon>
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    <div v-loading="isLoading" class="content-view">
      <el-table 
        v-if="viewStyle === 'list'"
        :data="tableData" 
        :height="tableHeight" 
        style="width: 100%" 
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="!isMobile" type="selection" width="55" />
        <el-table-column v-if="!isMobile" type="index" label="#" width="60" />
        <el-table-column
          prop="name"
          label="Name"
          :min-width="isMobile ? 190 : 280"
          show-overflow-tooltip
        >
          <template #default="scope">
            <div class="name-cell">
              <el-image :src="scope.row.cover_src || defaultCover" class="cover-img" fit="cover">
                <template #error>
                  <img :src="defaultCover" class="cover-img" />
                </template>
              </el-image>
              <span class="name-text" @click="openVideo(scope.row)" style="cursor: pointer">
                {{ scope.row.name }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="author_name"
          label="Author"
          :min-width="isMobile ? 120 : 180"
          show-overflow-tooltip
        >
          <template #default="scope">
            <el-link
              type="primary"
              @click="openAuthorVideos(scope.row)"
              style="cursor: pointer"
              v-if="scope.row.author_name"
            >
              {{ scope.row.author_name }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="path" label="Path" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="updatedAt" label="Updated" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="!isMobile" prop="rate" label="Rate" min-width="120" show-overflow-tooltip>
          <template #default="scope">
            <el-rate v-model="scope.row.rate" @change="changeRate(scope.row)" />
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
      <div v-else class="grid-view" :style="'height: ' + tableHeight">
        <div 
          v-for="item in tableData" 
          :key="item.id" 
          class="grid-item"
          :class="{ 'grid-item-selected': batchSelections.includes(item.id) }"
        >
          <div class="grid-item-image-wrapper" @click="openVideo(item)">
            <el-image :src="item.cover_src || defaultCover" class="grid-item-image" fit="cover">
              <template #error>
                <img :src="defaultCover" class="grid-item-image" />
              </template>
            </el-image>
            <div class="grid-item-name-overlay">
              <div class="grid-item-created-at">{{ item.createdAt || '-' }}</div>
              <div class="grid-item-name">{{ item.name }}</div>
            </div>
            <el-checkbox 
              :model-value="batchSelections.includes(item.id)"
              class="grid-item-checkbox"
              @click.stop
              @change="handleGridSelectionChange(item, $event)"
            />
          </div>
          <div class="grid-item-info">
            <div class="grid-item-author" @click.stop="openAuthorVideos(item)">
              <span v-if="item.author_name">{{ item.author_name }}</span>
              <span v-else>-</span>
            </div>
            <div class="grid-item-actions">
              <el-rate 
                v-model="item.rate" 
                @change="changeRate(item)" 
                @click.stop
                size="small"
              />
              <el-icon
                :color="item.is_favor ? '#f56c6c' : '#909399'"
                @click.stop="changeFavor(item)"
                style="cursor: pointer; font-size: 18px; margin-left: 8px"
              >
                <StarFilled v-if="item.is_favor" />
                <Star v-else />
              </el-icon>
              <el-dropdown @click.stop trigger="click">
                <el-icon style="cursor: pointer; font-size: 18px; margin-left: 8px">
                  <MoreFilled />
                </el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="op in operations"
                      @click="actionFn(item, op.flag)"
                      :key="op.flag"
                    >
                      <el-icon><component :is="op.icon"></component></el-icon>
                      <span style="margin-left: 5px">{{ op.label }}</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </div>
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
          <el-link v-if="k === 'Play URL' && v && v !== '-'" :href="v" type="primary" target="_blank">{{ v }}</el-link>
          <span v-else>{{ v }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, InfoFilled, Delete, Download, Grid, List, MoreFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { ajax } from '@/utils/request'
import { timeOption } from '@/config'

const defaultCover = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="20" height="20"%3E%3Crect width="20" height="20" fill="%23ddd"/%3E%3C/svg%3E'

const route = useRoute()
const router = useRouter()

const searchForm = ref({ limit: 40 })
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(40)
const searchInput = ref('')
const isLoading = ref(false)
const isLike = ref(null)
const isFavor = ref(null)

const starsSelection = ref(null)
const starsOptions = ref([0, 1, 2, 3, 4, 5])

const searchFormRef = ref(null)
const paginationRef = ref(null)

const drawer = ref(false)
const drawerContent = ref({})
const drawerTitle = ref('')

const batchSelections = ref([])
const batchOptions = ref([
  { label: 'Batch Delete', value: 'delete' },
  { label: 'Batch Download', value: 'download' },
])

const viewStyle = ref('list')
const tableHeight = ref(500)
const isMobile = ref(false)
const mobileFiltersExpanded = ref(false)

const updateMobileState = () => {
  isMobile.value = window.innerWidth <= 900
  if (!isMobile.value) {
    mobileFiltersExpanded.value = true
  }
}

const normalizeRandomQuery = (value) => {
  if (value === undefined || value === null || value === '') return undefined
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') {
    const lowered = value.toLowerCase()
    if (lowered === 'true') return true
    if (lowered === 'false') return false
  }
  return undefined
}

const getRandomQueryValue = (query) => {
  const normalizedValue = normalizeRandomQuery(query?.random)
  return normalizedValue === undefined ? undefined : String(normalizedValue)
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

const openVideo = (data) => {
  if (data.play_src) {
    window.open(data.play_src, '_blank')
  }
}

const openAuthorVideos = (data) => {
  console.log('data:',data)
  if (data.author) {
    router.push({
      name: 'dyauthor-detail',
      params: { id: data.author },
    })
  }
}

const changeRate = (data) => {
  ajax.patch(`/dy/video/${data.id}/`, { rate: data.rate }).catch((err) => {
    ElMessage.error('Failed to update rate')
  })
}

const changeFavor = (data) => {
  data.is_favor = !data.is_favor
  ajax.patch(`/dy/video/${data.id}/`, { is_favor: data.is_favor }).catch((err) => {
    data.is_favor = !data.is_favor
    ElMessage.error('Failed to update favor')
  })
}

const operations = ref([
  { label: 'Detail', flag: 'detail', icon: InfoFilled },
  { label: 'Delete', flag: 'delete', icon: Delete },
  { label: 'Download', flag: 'download', icon: Download },
])

const showDrawerFn = (item, flag) => {
  drawer.value = true
  drawerContent.value = {
    Name: item.name,
    ID: item.id,
    'Author Name': item.author_name || '-',
    'Author ID': item.author || '-',
    Path: item.path || '-',
    'Play URL': item.play_src || '-',
    Rate: item.rate || 0,
    'Is Like': item.is_like ? 'Yes' : 'No',
    Favor: item.is_favor ? 'Yes' : 'No',
    'Created At': item.createdAt || '-',
    'Updated At': item.updatedAt || '-',
  }
  drawerTitle.value = `${flag} of ${item.name}`
}

const actionFn = (data, flag) => {
  if (flag == 'detail') {
    showDrawerFn(data, 'Detail')
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
        .delete(`/dy/video/${data.id}/`)
        .then(() => {
          ElMessage.success(`${data.name} deleted`)
          getData()
        })
        .catch((err) => {
          ElMessage.error('Failed to delete')
        })
    }).catch(() => {})
  } else if (flag == 'download') {
    ajax.post(`/dy/video/download/`, { ids: [data.id] }).then((res) => {
      ElMessage.success(res.data.msg || 'Download started')
    }).catch((err) => {
      ElMessage.error('Failed to start download')
    })
  }
}

const toggleViewStyle = () => {
  viewStyle.value = viewStyle.value === 'list' ? 'grid' : 'list'
}

const handleSelectionChange = (selection) => {
  batchSelections.value = selection.map((x) => x.id)
}

const handleGridSelectionChange = (item, checked) => {
  const index = batchSelections.value.indexOf(item.id)
  if (checked && index === -1) {
    batchSelections.value.push(item.id)
  } else if (!checked && index > -1) {
    batchSelections.value.splice(index, 1)
  }
}

const onBatchOperation = (val) => {
  if (!val) return
  if (batchSelections.value.length === 0) {
    ElMessage.warning('Please select at least one video')
    return
  }
  
  if (val === 'delete') {
    const selectedData = tableData.value.filter(row => batchSelections.value.includes(row.id))
    const names = selectedData.map(row => row.name).join(', ')
    ElMessageBox.confirm(
      `Delete <span style='font-weight: bold;color:red;'>${batchSelections.value.length}</span> video(s)?<br/>${names}`,
      'Warning',
      {
        closeOnClickModal: false,
        dangerouslyUseHTMLString: true,
        type: 'warning',
      },
    ).then(() => {
      const deletePromises = batchSelections.value.map(id => ajax.delete(`/dy/video/${id}/`))
      Promise.all(deletePromises)
        .then(() => {
          ElMessage.success(`${batchSelections.value.length} video(s) deleted`)
          batchSelections.value = []
          getData()
        })
        .catch((err) => {
          ElMessage.error('Failed to delete some videos')
          getData()
        })
    }).catch(() => {})
  } else if (val === 'download') {
    ajax.post(`/dy/video/download/`, { ids: batchSelections.value }).then((res) => {
      ElMessage.success(res.data.msg || `Download started for ${batchSelections.value.length} video(s)`)
      batchSelections.value = []
    }).catch((err) => {
      const errorMsg = err.response?.data?.detail || err.response?.data?.message || 'Failed to start download'
      ElMessage.error(errorMsg)
    })
  }
}

const searchFn = () => {
  let query = {
    ...searchForm.value,
    search: searchInput.value,
    rate: starsSelection.value,
    is_like: isLike.value,
    is_favor: isFavor.value,
  }
  // Remove page parameter when performing new search
  delete query.page
  if (!query.search) delete query.search
  if (starsSelection.value === null) delete query.rate
  if (isLike.value === null) delete query.is_like
  if (isFavor.value === null) delete query.is_favor
  const randomQueryValue = getRandomQueryValue(route.query)
  if (randomQueryValue !== undefined) query.random = randomQueryValue
  else delete query.random
  
  router.push({
    ...route,
    query: query,
  })
}

const handleSizeChange = (x) => {
  const query = { ...searchForm.value, limit: x, page: 1 }
  const randomQueryValue = getRandomQueryValue(route.query)
  if (randomQueryValue !== undefined) query.random = randomQueryValue
  else delete query.random
  router.push({ ...route, query })
}

const handleCurrentChange = (x) => {
  const query = { ...searchForm.value, page: x }
  const randomQueryValue = getRandomQueryValue(route.query)
  if (randomQueryValue !== undefined) query.random = randomQueryValue
  else delete query.random
  router.push({ ...route, query })
}

const dataTransfer = (data) => {
  data.createdAt = new Date(data.created_at).toLocaleString('zh-CN', timeOption)
  data.updatedAt = new Date(data.updated_at).toLocaleString('zh-CN', timeOption)
  data.name = data.desc || data.name
}

const getData = async () => {
  let params = { ...searchForm.value }
  isLoading.value = true
  try {
    const res = await ajax.get('/dy/video/', params)
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
    ElMessage.error('Failed to load videos')
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
  pageSize.value = searchForm.value.limit ? Number(searchForm.value.limit) : 40
  currentPage.value = searchForm.value.page ? Number(searchForm.value.page) : 1
  searchForm.value.limit = pageSize.value
  
  if (searchForm.value.rate) starsSelection.value = Number(searchForm.value.rate)
  else starsSelection.value = null
  if (searchForm.value.is_like !== undefined)
    isLike.value = searchForm.value.is_like === 'true' || searchForm.value.is_like === true
  else isLike.value = null
  if (searchForm.value.is_favor !== undefined)
    isFavor.value = searchForm.value.is_favor === 'true' || searchForm.value.is_favor === true
  else isFavor.value = null
  const randomQueryValue = getRandomQueryValue(route.query)
  if (randomQueryValue !== undefined) searchForm.value.random = randomQueryValue
  else delete searchForm.value.random
  
  getData()
}

// Watch for route query changes
watch(() => route.query, () => {
  loadDataFromRoute()
}, { deep: true })

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
</script>

<style scoped>
.name-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}

.cover-img {
  width: 20px;
  height: 20px;
  margin-right: 8px;
  border-radius: 5%;
  flex-shrink: 0;
}

.list-item-operation {
  margin-right: 10px;
  cursor: pointer;
}

.name-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-view {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  /* padding: 10px; */
  overflow-y: auto;
  grid-auto-rows: max-content;
}

.grid-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: visible;
  cursor: pointer;
  transition: all 0.3s;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.grid-item:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.grid-item-selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.grid-item-image-wrapper {
  position: relative;
  width: 100%;
  padding-top: 177.78%;
  background: #f5f7fa;
  flex-shrink: 0;
}

.grid-item-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.grid-item-name-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
  color: #fff;
  font-size: 14px;
  padding: 8px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.grid-item-created-at {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.grid-item-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.grid-item-checkbox {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 10;
  opacity: 0;
  transition: opacity 0.3s;
}

.grid-item:hover .grid-item-checkbox {
  opacity: 1;
}

.grid-item-info {
  padding: 10px;
  flex-shrink: 0;
}

.grid-item-author {
  font-size: 12px;
  color: #409eff;
  margin-bottom: 8px;
  cursor: pointer;
}

.grid-item-author:hover {
  text-decoration: underline;
}

.grid-item-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
