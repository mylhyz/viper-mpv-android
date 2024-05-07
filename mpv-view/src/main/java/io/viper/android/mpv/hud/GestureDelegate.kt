package io.viper.android.mpv.hud

import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import io.viper.android.mpv.OverlayAdapter
import io.viper.android.mpv.PlayerAdapter
import kotlin.math.abs
import kotlin.math.roundToInt

class GestureDelegate(
    private var adapter: PlayerAdapter,
    private var overlay: OverlayAdapter,
    var screenConfig: ScreenConfig,
    var touchConfig: TouchConfig
) {
    var numberOfTaps = 0
    var lastTapTimeMs: Long = 0
    var touchDownMs: Long = 0

    private var touchAction = TOUCH_NONE
    private var initTouchY = 0f
    private var initTouchX = 0f
    private var touchY = -1f
    private var touchX = -1f
    private var verticalTouchActive = false

    private var touchControlFlags: Int = 0


    fun syncSettings(prefs: SharedPreferences, resources: Resources) {
        // TODO 控制支持的手势
        touchControlFlags =
            TOUCH_FLAG_AUDIO_VOLUME + TOUCH_FLAG_BRIGHTNESS + TOUCH_FLAG_DOUBLE_TAP_SEEK + TOUCH_FLAG_PLAY + TOUCH_FLAG_SWIPE_SEEK + TOUCH_FLAG_SCREENSHOT + TOUCH_FLAG_SCALE
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO 处理缩放
        // 处理手势
        val xChanged = if (touchX != -1f && touchY != -1f) event.x - touchX else 0f
        val yChanged = if (touchX != -1f && touchY != -1f) event.y - touchY else 0f

        // 用于确定当前的偏移量的指向更偏向横向还是纵向
        // coefficient is the gradient's move to determine a neutral zone
        val coefficient = abs(yChanged / xChanged)
        val xGestureSize = xChanged / screenConfig.metrics.xdpi * 2.54f
        // 计算Y方向上的偏移量
        val deltaY =
            ((abs(initTouchY - event.y) / screenConfig.metrics.xdpi + 0.5f) * 2f).coerceAtLeast(1f)

        val now = System.currentTimeMillis()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownMs = now // 记录一下DOWN事件的时间
                verticalTouchActive = false // 是否激活了垂直手势
                // Audio
                initTouchX = event.x
                initTouchY = event.y
                touchY = initTouchY
                initAudioVolume()
                initBrightness()
                touchAction = TOUCH_NONE
                // Seek
                touchX = event.x
                Log.i(TAG, "down")
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchAction == TOUCH_IGNORE) return false
                Log.i(TAG, "move $touchAction coefficient=$coefficient $xGestureSize $deltaY")
                if (touchAction != TOUCH_TAP_SEEK && coefficient > 2) {
                    // 如果不是在执行SEEK ACTION过程，就可以判断是不是需要执行垂直手势
                    // 并且系数coefficient大于2（大于1则表示y方向上的变化要超过x）
                    if (!verticalTouchActive) {
                        if (abs(yChanged / screenConfig.yRange) >= 0.05f) {
                            // 如果Y方向上的变化大于5%则激活垂直方向手势
                            verticalTouchActive = true
                            touchY = event.y
                            touchX = event.x
                        }
                        return false
                    }
                    touchY = event.y
                    touchX = event.x
                    doVerticalTouchAction(yChanged)
                } else if (initTouchX < screenConfig.metrics.widthPixels * 0.95f) {
                    doSeekTouchAction(deltaY.roundToInt(), xGestureSize)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (touchAction == TOUCH_IGNORE) touchAction = TOUCH_NONE
                touchY = -1f
                touchX = -1f
                Log.i(TAG, "up $touchAction")
                if (touchAction == TOUCH_TAP_SEEK) {
                    doSeekTouchAction(deltaY.roundToInt(), xGestureSize)
                    return true
                }
                if (touchAction == TOUCH_VOLUME || touchAction == TOUCH_BRIGHTNESS) {
                    doVerticalTouchAction(yChanged)
                    return true
                }

                if (now - touchDownMs > ViewConfiguration.getDoubleTapTimeout()) {
                    // 如果当前UP事件和DOWN事件的间隔过大超过了双击的超时时间，则重置多次点击事件判断
                    numberOfTaps = 0
                    lastTapTimeMs = 0
                }

                val touchSlop = touchConfig.touchSlop

                if (abs(event.x - initTouchX) < touchSlop && abs(event.y - initTouchY) < touchSlop) {
                    // 如果点击区域范围在一个阈值内，则认为只是点击事件
                    if (numberOfTaps > 0 && now - lastTapTimeMs < ViewConfiguration.getDoubleTapTimeout()) {
                        numberOfTaps += 1
                    } else {
                        numberOfTaps = 1
                    }
                }

                lastTapTimeMs = now

                if (numberOfTaps > 1) {
                    // 处理连续点击
                    val range =
                        (if (screenConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) screenConfig.xRange else screenConfig.yRange).toFloat()
                    // TODO 根据点击位置判断连点是执行快进还是后退还是暂停
                    Log.i(TAG, "multi tap")
                } else {
                    Log.i(TAG, "tap")
                }
            }
        }

        // FIXME 这里需要重新处理返回false的逻辑，接收到DOWN的情况下必须返回true才能继续接收
//        return touchAction != TOUCH_NONE
        return true
    }

    private fun doVolumeTouch(yAxisChanged: Float) {
        if (touchAction != TOUCH_NONE && touchAction != TOUCH_VOLUME) return
        val audioMax = adapter.audioMax
        // 计算delta的值，注意加上负号是因为y轴的方向是从上到下，所以向下滑动y值是增大的
        // 因此这里加上负号是为了规范delta，大于0表示音量+，小于0表示音量-
        val delta = -(yAxisChanged / screenConfig.yRange * audioMax * 1.25f)
        // 在之前获取到的当前音量的基础上进行计算
        adapter.volume += delta
        // 对计算的音量结果进行范围限制 0-100 之间
        val vol = adapter.volume.toInt().coerceIn(0, audioMax)
        if (delta != 0f) {
            adapter.setAudioVolume(vol)
        }
        touchAction = TOUCH_VOLUME
    }

    private fun doBrightnessTouch(yAxisChanged: Float) {
        if (touchAction != TOUCH_NONE && touchAction != TOUCH_BRIGHTNESS) return
        touchAction = TOUCH_BRIGHTNESS
        // 计算亮度delta的值，注意加上负号是因为y轴的方向是从上到下，所以向下滑动y值是增大的
        // 这里计算得到的delta是一个浮点数，含义是占纵向总高度的百分比 * 1.25倍
        // Set delta : 1.25f is arbitrary for now, it possibly will change in the future
        val delta = -yAxisChanged / screenConfig.yRange * 1.25f
        adapter.setBrightness(delta)
    }

    private fun doVerticalTouchAction(yAxisChanged: Float) {
        // 划分屏幕区域为左侧，中央，右侧，分别处理不同的点击事件回调
        // [ Left 3/7 ] [    Center    ] [ Right 3/7 ]

        // 先判断点击区域是否在规划的左侧还是右侧
        val rightAction = touchX.toInt() > 4 * screenConfig.metrics.widthPixels / 7f
        val leftAction = !rightAction && touchX.toInt() < 3 * screenConfig.metrics.widthPixels / 7f
        if (!leftAction && !rightAction) return
        // 判断是否支持左侧和右侧的手势事件
        val audio = touchControlFlags and TOUCH_FLAG_AUDIO_VOLUME != 0
        val brightness = touchControlFlags and TOUCH_FLAG_BRIGHTNESS != 0
        if (!audio && !brightness) return
        // 处理手势
        // 支持只启用单个手势操作，比如只启用了音量调节，则左右都是调节音量
        // 否则调节规则是 左侧是亮度，右侧是音量
        if (rightAction) {
            if (audio) doVolumeTouch(yAxisChanged)
            else doBrightnessTouch(yAxisChanged)
        } else {
            if (brightness) doBrightnessTouch(yAxisChanged)
            else doVolumeTouch(yAxisChanged)
        }
    }

    private fun doSeekTouchAction(deltaY: Int, xGestureSize: Float) {
        // TODO 还有一种情况是媒体不支持seek，也直接返回，这里暂时忽略
        if (touchControlFlags and TOUCH_FLAG_SWIPE_SEEK == 0) return
        var realDeltaY = deltaY
        if (realDeltaY == 0) realDeltaY = 1
        if (abs(xGestureSize) < 1) return
        // 忽略touchAction的其他情况
        if (touchAction != TOUCH_NONE && touchAction != TOUCH_TAP_SEEK) return
        touchAction = TOUCH_TAP_SEEK
        // TODO 根据媒体的长度和当前进度来进行seek
        Log.i(TAG, "seek")
    }

    private fun initAudioVolume() {
        adapter.initAudioVolume()
    }

    private fun initBrightness() {
        adapter.initBrightness()
    }

    companion object {
        const val TAG = "mpv.GestureDelegate"

        // 支持的所有手势功能,可选
        const val TOUCH_FLAG_AUDIO_VOLUME = 1
        const val TOUCH_FLAG_BRIGHTNESS = 1 shl 1
        const val TOUCH_FLAG_DOUBLE_TAP_SEEK = 1 shl 2
        const val TOUCH_FLAG_PLAY = 1 shl 3
        const val TOUCH_FLAG_SWIPE_SEEK = 1 shl 4
        const val TOUCH_FLAG_SCREENSHOT = 1 shl 5
        const val TOUCH_FLAG_SCALE = 1 shl 6

        //Touch Events
        private const val TOUCH_NONE = 0
        private const val TOUCH_VOLUME = 1
        private const val TOUCH_BRIGHTNESS = 2
        private const val TOUCH_MOVE = 3
        private const val TOUCH_TAP_SEEK = 4
        private const val TOUCH_IGNORE = 5
        private const val TOUCH_SCREENSHOT = 6
    }
}