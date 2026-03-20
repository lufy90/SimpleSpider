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
            v-if="hasNext"
            type="button"
            class="video-preview-arrow video-preview-arrow-right"
            aria-label="Next"
            @click.stop="goNext"
          >
            <el-icon :size="32"><ArrowRight /></el-icon>
          </button>

          <div v-if="videos.length > 1" class="video-preview-counter">
            {{ currentIndex + 1 }} / {{ videos.length }}
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Close, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  videos: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
})

const emit = defineEmits(['close'])

const videoRef = ref(null)
const currentIndex = ref(0)

const currentVideo = computed(() => {
  const list = props.videos
  if (!list.length) return null
  const idx = Math.max(0, Math.min(currentIndex.value, list.length - 1))
  return list[idx]
})

const hasPrev = computed(() => props.videos.length > 1 && currentIndex.value > 0)
const hasNext = computed(() =>
  props.videos.length > 1 && currentIndex.value < props.videos.length - 1
)

function goPrev() {
  if (!hasPrev.value) return
  currentIndex.value--
}

function goNext() {
  if (!hasNext.value) return
  currentIndex.value++
}

function handleClose() {
  emit('close')
}

function onVideoEnded() {
  if (hasNext.value) {
    goNext()
  }
}

function onKeydown(e) {
  if (!props.visible) return
  if (e.key === 'Escape') {
    handleClose()
    return
  }
  if (props.videos.length <= 1) return
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    e.preventDefault()
    if (hasPrev.value) goPrev()
  } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    e.preventDefault()
    if (hasNext.value) goNext()
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      currentIndex.value = Math.min(
        Math.max(0, props.initialIndex),
        Math.max(0, props.videos.length - 1)
      )
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  }
)

watch(
  () => [props.initialIndex, props.videos.length],
  () => {
    if (props.visible) {
      currentIndex.value = Math.min(
        Math.max(0, props.initialIndex),
        Math.max(0, props.videos.length - 1)
      )
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
