<template>
  <div class="dyauthor-detail-container">
    <div v-loading="isLoadingAuthor" class="author-info">
      <div class="section-header">
        <span>Author Information</span>
        <el-button @click="router.back()" size="small">Back</el-button>
      </div>
      <div class="author-info-content" v-if="authorData">
        <div class="author-avatar-section">
          <el-image :src="authorData.avatar_src || defaultAvatar" class="author-avatar" fit="cover">
            <template #error>
              <img :src="defaultAvatar" class="author-avatar" />
            </template>
          </el-image>
        </div>
        <el-descriptions :column="2" border class="author-descriptions">
        <el-descriptions-item label="Name">{{ authorData.name }}</el-descriptions-item>
        <el-descriptions-item label="ID">{{ authorData.id }}</el-descriptions-item>
        <el-descriptions-item label="Unique ID">{{ authorData.unique_id }}</el-descriptions-item>
        <el-descriptions-item label="Status">{{ statusOptions[authorData.status] || authorData.status }}</el-descriptions-item>
        <el-descriptions-item label="Path">{{ authorData.path }}</el-descriptions-item>
        <el-descriptions-item label="URL">
          <el-link v-if="authorData.url" :href="authorData.url" type="primary" target="_blank">{{ authorData.url }}</el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Rate">
          <el-rate v-model="authorData.rate" @change="changeRate" />
        </el-descriptions-item>
        <el-descriptions-item label="Favor">
          <el-icon
            :color="authorData.is_favor ? '#f56c6c' : '#909399'"
            @click="changeFavor"
            style="cursor: pointer; font-size: 20px"
          >
            <StarFilled v-if="authorData.is_favor" />
            <Star v-else />
          </el-icon>
        </el-descriptions-item>
        <el-descriptions-item label="Valid">
          <el-switch v-model="authorData.is_valid" @change="changeValid" />
        </el-descriptions-item>
        <el-descriptions-item label="Desc" v-if="authorData.desc">
          <div class="desc-links">
            <template v-if="descLinkItems.length > 0">
              <template v-for="(item, idx) in descLinkItems" :key="idx">
                <el-link
                  v-if="item.url"
                  :href="item.url"
                  type="primary"
                  target="_blank"
                  class="desc-link"
                >{{ item.text }}</el-link>
                <span v-else class="desc-text">{{ item.text }}</span>
              </template>
            </template>
            <span v-else class="desc-raw">{{ authorData.desc }}</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="Used Names">{{ usedNamesDisplay }}</el-descriptions-item>
        <el-descriptions-item label="Last Crawled">{{ authorData.lastCrawled || 'Never' }}</el-descriptions-item>
        <el-descriptions-item label="Created At">{{ authorData.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Updated At">{{ authorData.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
    <div class="videos-section">
      <div class="section-header">
        <span>Videos <span v-if="videoTotal > 0" style="font-weight: normal; color: #909399;">({{ videoTotal }})</span></span>
      </div>
      <div v-loading="isLoadingVideos" class="videos-grid" ref="videosGridRef">
        <div v-for="(item, index) in videoData" class="grid-item" :key="item.id" @click="openVideo(item, index)">
          <div class="grid-item-image-wrapper">
            <el-image class="grid-item-icon" :src="item.cover_src || defaultCover" fit="cover">
              <template #error>
                <img :src="defaultCover" class="grid-item-icon" />
              </template>
            </el-image>
            <div class="grid-item-name-overlay">
              <div class="grid-item-created-at">{{ item.createdAt || '-' }}</div>
              <div class="grid-item-name">{{ item.name }}</div>
            </div>
          </div>
          <div class="grid-item-actions">
            <div @click.stop>
              <el-rate v-model="item.rate" @change="changeVideoRate(item)" size="small" />
            </div>
            <el-icon
              :color="item.is_favor ? '#f56c6c' : '#909399'"
              @click.stop="changeVideoFavor(item)"
              style="cursor: pointer; font-size: 18px; margin-left: 8px;"
            >
              <StarFilled v-if="item.is_favor" />
              <Star v-else />
            </el-icon>
          </div>
        </div>
      </div>
    </div>
    <VideoPreview
      :visible="videoPreviewVisible"
      :videos="videoData"
      :initial-index="videoPreviewInitialIndex"
      @close="videoPreviewVisible = false"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { ajax } from '@/utils/request'
import { timeOption } from '@/config'
import VideoPreview from '@/components/VideoPreview.vue'

const DOUYIN_USER_BASE = 'https://www.douyin.com/user/'

function parseDescToLinkItems(desc) {
  if (!desc || typeof desc !== 'string') return []
  const items = []
  const tokens = desc.split(/[@\n]+/).map((t) => t.trim()).filter(Boolean)
  for (const token of tokens) {
    const parts = token.split(',').map((p) => p.trim()).filter(Boolean)
    for (const part of parts) {
      if (part.length >= 8 && !/\s/.test(part)) {
        items.push({ text: part, url: DOUYIN_USER_BASE + part })
      } else if (part.length > 0) {
        items.push({ text: part, url: null })
      }
    }
  }
  return items
}

const defaultCover = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="20" height="20"%3E%3Crect width="20" height="20" fill="%23ddd"/%3E%3C/svg%3E'
const defaultAvatar = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="100" height="100"%3E%3Crect width="100" height="100" fill="%23ddd"/%3E%3C/svg%3E'

const route = useRoute()
const router = useRouter()

const authorData = ref(null)
const isLoadingAuthor = ref(false)
const isLoadingVideos = ref(false)
const videoData = ref([])
const videoTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(40)
const videosGridRef = ref(null)
const hasMore = ref(true)
const isLoadingMore = ref(false)
const videoPreviewVisible = ref(false)
const videoPreviewInitialIndex = ref(0)

const statusOptions = {
  ready: 'Ready',
  waiting: 'Waiting',
  running: 'Running',
  error: 'Error',
}

const descLinkItems = computed(() => {
  const author = authorData.value
  return author?.desc ? parseDescToLinkItems(author.desc) : []
})

const usedNamesDisplay = computed(() => {
  const author = authorData.value
  if (!author) return '-'
  const un = author.used_names
  if (un == null || un === '') return '-'
  return Array.isArray(un) ? un.join(', ') : String(un)
})

const getAuthorData = async () => {
  const authorId = route.params.id
  if (!authorId) {
    ElMessage.error('Author ID is required')
    router.push('/dyauthor')
    return
  }
  
  isLoadingAuthor.value = true
  try {
    const res = await ajax.get(`/dy/author/${authorId}/`)
    const data = res.data
    data.createdAt = new Date(data.created_at).toLocaleString('zh-CN', timeOption)
    data.updatedAt = new Date(data.updated_at).toLocaleString('zh-CN', timeOption)
    if (data.last_crawl)
      data.lastCrawled = new Date(data.last_crawl).toLocaleString('zh-CN', timeOption)
    else data.lastCrawled = 'Never'
    authorData.value = data
  } catch (error) {
    ElMessage.error('Failed to load author data')
    router.push('/dyauthor')
  } finally {
    isLoadingAuthor.value = false
  }
}

const getVideoData = async (append = false) => {
  const authorId = route.params.id
  if (!authorId) return
  
  if (append) {
    isLoadingMore.value = true
  } else {
    isLoadingVideos.value = true
    currentPage.value = 1
    videoData.value = []
    hasMore.value = true
  }
  
  try {
    const params = {
      author: authorId,
      limit: pageSize.value,
      page: currentPage.value,
    }
    const res = await ajax.get('/dy/video/', params)
    let data = res.data.results || res.data
    videoTotal.value = res.data.count || data.length
    
    if (Array.isArray(data)) {
      data.forEach((x) => {
        x.createdAt = new Date(x.created_at).toLocaleString('zh-CN', timeOption)
        x.updatedAt = new Date(x.updated_at).toLocaleString('zh-CN', timeOption)
        x.name = x.desc || x.name
      })
      
      if (append) {
        videoData.value = [...videoData.value, ...data]
      } else {
        videoData.value = data
      }
      
      if (data.length < pageSize.value || videoData.value.length >= videoTotal.value) {
        hasMore.value = false
      }
    } else {
      if (!append) {
        videoData.value = []
      }
      hasMore.value = false
    }
  } catch (error) {
    ElMessage.error('Failed to load videos')
    if (!append) {
      videoData.value = []
    }
  } finally {
    isLoadingVideos.value = false
    isLoadingMore.value = false
  }
}

const handleScroll = () => {
  if (isLoadingMore.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const scrollHeight = document.documentElement.scrollHeight
  const clientHeight = document.documentElement.clientHeight
  
  if (scrollHeight - scrollTop - clientHeight < 100) {
    currentPage.value++
    getVideoData(true)
  }
}

const changeRate = () => {
  ajax.patch(`/dy/author/${authorData.value.id}/`, { rate: authorData.value.rate }).catch((err) => {
    ElMessage.error('Failed to update rate')
  })
}

const changeFavor = () => {
  authorData.value.is_favor = !authorData.value.is_favor
  ajax.patch(`/dy/author/${authorData.value.id}/`, { is_favor: authorData.value.is_favor }).catch((err) => {
    authorData.value.is_favor = !authorData.value.is_favor
    ElMessage.error('Failed to update favor')
  })
}

const changeValid = () => {
  ajax.patch(`/dy/author/${authorData.value.id}/`, { is_valid: authorData.value.is_valid }).catch((err) => {
    authorData.value.is_valid = !authorData.value.is_valid
    ElMessage.error('Failed to update valid')
  })
}

const openVideo = (data, index) => {
  videoPreviewInitialIndex.value = typeof index === 'number' ? index : videoData.value.findIndex((v) => v.id === data.id)
  if (videoPreviewInitialIndex.value < 0) videoPreviewInitialIndex.value = 0
  videoPreviewVisible.value = true
}

const changeVideoRate = (data) => {
  ajax.patch(`/dy/video/${data.id}/`, { rate: data.rate }).catch((err) => {
    ElMessage.error('Failed to update rate')
  })
}

const changeVideoFavor = (data) => {
  data.is_favor = !data.is_favor
  ajax.patch(`/dy/video/${data.id}/`, { is_favor: data.is_favor }).catch((err) => {
    data.is_favor = !data.is_favor
    ElMessage.error('Failed to update favor')
  })
}


onMounted(() => {
  getAuthorData()
  getVideoData()
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

watch(() => route.params.id, () => {
  getAuthorData()
  getVideoData()
})
</script>

<style scoped>
.dyauthor-detail-container {
  padding: 20px;
}

.author-info {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  font-size: 18px;
  font-weight: bold;
}

.author-info-content {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.author-avatar-section {
  flex-shrink: 0;
}

.author-avatar {
  width: 240px;
  height: 240px;
  border-radius: 50%;
  border: 2px solid #e4e7ed;
}

.author-descriptions {
  flex: 1;
}

.desc-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: center;
}

.desc-link {
  margin-right: 0;
}

.desc-text {
  margin-right: 4px;
}

.desc-raw {
  white-space: pre-wrap;
  word-break: break-word;
}

.videos-section {
  margin-top: 20px;
}

.videos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  padding: 10px;
}

.grid-item {
  position: relative;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;
}

.grid-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.grid-item-image-wrapper {
  position: relative;
  width: 100%;
  padding-top: 160%;
  /* padding-top: 177.78%; */
  background: #f5f7fa;
}

.grid-item-icon {
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

.grid-item-actions {
  display: flex;
  align-items: center;
  padding: 8px;
  justify-content: space-between;
}
</style>

