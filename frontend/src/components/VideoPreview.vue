<template>
  <Teleport to="body">
    <Transition name="video-preview-fade">
      <div
        v-if="visible"
        class="video-preview-mask"
        @click.self="handleClose"
      >
        <div class="video-preview-wrapper">
          <button
            type="button"
            class="video-preview-close"
            aria-label="Close"
            @click="handleClose"
          >
            <el-icon :size="24"><Close /></el-icon>
          </button>

          <button
            v-if="hasPrev"
            type="button"
            class="video-preview-arrow video-preview-arrow-left"
            aria-label="Previous"
            @click.stop="goPrev"
          >
            <el-icon :size="32"><ArrowLeft /></el-icon>
          </button>

          <div class="video-preview-content">
            <template v-if="currentVideo && currentVideo.play_src">
              <video
                ref="videoRef"
                :key="currentIndex"
                :src="currentVideo.play_src"
                class="video-preview-player"
                controls
                autoplay
                @ended="onVideoEnded"
              />
              <div v-if="currentVideo.name" class="video-preview-title">
                {{ currentVideo.name }}
              </div>
            </template>
            <div v-else class="video-preview-no-src">
              No playable source
            </div>
          </div>

          <button
            v-if="showNextControl"
            type="button"
            class="video-preview-arrow video-preview-arrow-right"
            aria-label="Next"
            :disabled="loadingMore && atLoadedEnd"
            @click.stop="goNext"
          >
            <el-icon v-if="loadingMore && atLoadedEnd" :size="32" class="is-loading"><Loading /></el-icon>
            <el-icon v-else :size="32"><ArrowRight /></el-icon>
          </button>

          <div v-if="videos.length > 0" class="video-preview-counter">
            {{ currentIndex + 1 }} / {{ displayTotal }}
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Close, ArrowLeft, ArrowRight, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  videos: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  hasMore: { type: Boolean, default: false },
  loadingMore: { type: Boolean, default: false },
  totalCount: { type: Number, default: 0 },
})

const emit = defineEmits(['close', 'load-more'])

const videoRef = ref(null)
const currentIndex = ref(0)
const waitingForMore = ref(false)

const clampIndex = (index) => {
  const max = Math.max(0, props.videos.length - 1)
  return Math.min(Math.max(0, index), max)
}

const currentVideo = computed(() => {
  const list = props.videos
  if (!list.length) return null
  return list[clampIndex(currentIndex.value)]
})

const atLoadedEnd = computed(
  () => props.videos.length > 0 && currentIndex.value >= props.videos.length - 1
)

const hasPrev = computed(() => props.videos.length > 1 && currentIndex.value > 0)

const hasNextInList = computed(
  () => props.videos.length > 1 && currentIndex.value < props.videos.length - 1
)

const hasNext = computed(() => hasNextInList.value || props.hasMore)

const showNextControl = computed(() => props.videos.length > 1 || props.hasMore)

const displayTotal = computed(() => {
  if (props.totalCount > 0) return props.totalCount
  return props.videos.length
})

function maybePrefetch() {
  if (!props.hasMore || props.loadingMore || props.videos.length === 0) return
  const prefetchFrom = Math.max(0, props.videos.length - 2)
  if (currentIndex.value >= prefetchFrom) {
    emit('load-more')
  }
}

function goPrev() {
  if (!hasPrev.value) return
  currentIndex.value--
}

function goNext() {
  if (hasNextInList.value) {
    currentIndex.value++
    maybePrefetch()
    return
  }
  if (props.hasMore && !props.loadingMore) {
    waitingForMore.value = true
    emit('load-more')
  }
}

function handleClose() {
  waitingForMore.value = false
  emit('close')
}

function onVideoEnded() {
  if (hasNextInList.value || props.hasMore) {
    goNext()
  }
}

function onKeydown(e) {
  if (!props.visible) return
  if (e.key === 'Escape') {
    handleClose()
    return
  }
  if (props.videos.length <= 1 && !props.hasMore) return
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    e.preventDefault()
    if (hasPrev.value) goPrev()
  } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    e.preventDefault()
    if (hasNext.value && !(props.loadingMore && atLoadedEnd.value)) goNext()
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      currentIndex.value = clampIndex(props.initialIndex)
      waitingForMore.value = false
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
      maybePrefetch()
    } else {
      waitingForMore.value = false
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  }
)

watch(
  () => props.videos.length,
  (newLen, oldLen) => {
    if (!props.visible) return
    if (newLen < oldLen) {
      currentIndex.value = clampIndex(currentIndex.value)
      return
    }
    if (newLen > oldLen) {
      if (waitingForMore.value) {
        waitingForMore.value = false
        if (currentIndex.value < newLen - 1) {
          currentIndex.value++
        }
      }
      maybePrefetch()
    }
  }
)

watch(
  () => props.loadingMore,
  (loading, prev) => {
    if (prev && !loading && waitingForMore.value) {
      waitingForMore.value = false
    }
  }
)
</script>

<style scoped>
.video-preview-fade-enter-active,
.video-preview-fade-leave-active {
  transition: opacity 0.2s ease;
}
.video-preview-fade-enter-from,
.video-preview-fade-leave-to {
  opacity: 0;
}

.video-preview-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-preview-wrapper {
  position: relative;
  width: 100%;
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-preview-close {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2001;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}
.video-preview-close:hover {
  background: rgba(255, 255, 255, 0.35);
}

.video-preview-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2001;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s, opacity 0.2s;
}
.video-preview-arrow:hover {
  background: rgba(255, 255, 255, 0.35);
}
.video-preview-arrow:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.video-preview-arrow .is-loading {
  animation: video-preview-spin 1s linear infinite;
}
@keyframes video-preview-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.video-preview-arrow-left {
  left: 24px;
}
.video-preview-arrow-right {
  right: 24px;
}

.video-preview-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 90vw;
  max-height: 90vh;
}

.video-preview-player {
  max-width: 90vw;
  max-height: 85vh;
  object-fit: contain;
  background: #000;
  border-radius: 4px;
}

.video-preview-title {
  margin-top: 12px;
  padding: 0 24px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  text-align: center;
  max-width: 80vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-preview-no-src {
  padding: 48px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 16px;
}

.video-preview-counter {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2001;
  padding: 6px 14px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 14px;
  border-radius: 4px;
}
</style>
