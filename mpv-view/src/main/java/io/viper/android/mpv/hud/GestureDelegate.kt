package io.viper.android.mpv.hud

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import io.viper.android.mpv.view.R
import kotlin.math.abs
import kotlin.math.min

enum class PropertyChange {
    Init,
    Seek,
    Volume,
    Bright,
    Finalize,

    /* Tap gestures */
    SeekFixed,
    PlayPause,
    Custom,
}

interface TouchGesturesObserver {
    fun onPropertyChange(p: PropertyChange, diff: Float)
}

class GestureDelegate(private val observer: TouchGesturesObserver) {

    // 屏幕宽度
    private var width: Float = 0f

    // 屏幕高度
    private var height: Float = 0f

    // minimum movement which triggers a Control state
    private var trigger: Float = 0f

    // where user initially placed their finger (ACTION_DOWN)
    private var initialPos = PointF()

    // timestamp of the last tap (ACTION_UP)
    private var lastTapTime = 0L

    // when the current gesture began
    private var lastDownTime = 0L

    // last non-throttled processed position
    private var lastPos = PointF()

    private var state = State.Up

    // relevant movement direction for the current state (0=H, 1=V)
    private var stateDirection = 0

    // which property change should be invoked where
    private var gestureHorizontal = State.Down
    private var gestureVertLeft = State.Down
    private var gestureVertRight = State.Down

    private var tapGestureLeft: PropertyChange? = null
    private var tapGestureCenter: PropertyChange? = null
    private var tapGestureRight: PropertyChange? = null

    fun setMetrics(width: Float, height: Float) {
        this.width = width
        this.height = height
        trigger = min(width, height) / TRIGGER_RATE
    }

    fun syncSettings(prefs: SharedPreferences, resources: Resources) {
        val get: (String, Int) -> String = { key, defaultRes ->
            val v = prefs.getString(key, "")
            if (v.isNullOrEmpty()) resources.getString(defaultRes) else v
        }
        val map = mapOf(
            "bright" to State.ControlBright,
            "seek" to State.ControlSeek,
            "volume" to State.ControlVolume
        )
        val map2 = mapOf(
            "seek" to PropertyChange.SeekFixed,
            "playpause" to PropertyChange.PlayPause,
            "custom" to PropertyChange.Custom
        )

        gestureHorizontal = map[get("gesture_horiz", R.string.pref_gesture_horizontal_default)] ?: State.Down
        gestureVertLeft = map[get("gesture_vert_left", R.string.pref_gesture_vert_left_default)] ?: State.Down
        gestureVertRight = map[get("gesture_vert_right", R.string.pref_gesture_vert_right_default)] ?: State.Down
        tapGestureLeft = map2[get("gesture_tap_left", R.string.pref_gesture_tap_left_default)]
        tapGestureCenter = map2[get("gesture_tap_center", R.string.pref_gesture_tap_center_default)]
        tapGestureRight = map2[get("gesture_tap_right", R.string.pref_gesture_tap_right_default)]
    }

    private fun processTap(p: PointF): Boolean {
        if (state == State.Up) {
            lastDownTime = SystemClock.uptimeMillis()
            // 3 is another arbitrary value here that seems good enough
            if (PointF(lastPos.x - p.x, lastPos.y - p.y).length() > trigger * 3)
                lastTapTime = 0 // last tap was too far away, invalidate
            return true
        }
        // discard if any movement gesture took place
        if (state != State.Down)
            return false

        // 执行到下面流程必然是在接收了DOWN事件之后
        val now = SystemClock.uptimeMillis()
        if (now - lastDownTime >= TAP_DURATION) {
            // 如果上次DOWN和这次DOWN事件之间间隔超过 TAP_DURATION，则认为是按住，重置tagTime
            lastTapTime = 0 // finger was held too long, reset
            return false
        }
        if (now - lastTapTime < TAP_DURATION) {
            // 划分屏幕区域为左侧，中央，右侧，分别处理不同的点击事件回调
            // [ Left 28% ] [    Center    ] [ Right 28% ]
            if (p.x <= width * 0.28f)
                tapGestureLeft?.let { sendPropertyChange(it, -1f); return true }
            else if (p.x >= width * 0.72f)
                tapGestureRight?.let { sendPropertyChange(it, 1f); return true }
            else
                tapGestureCenter?.let { sendPropertyChange(it, 0f); return true }
            lastTapTime = 0
        } else {
            lastTapTime = now
        }
        return false
    }

    private fun processMovement(p: PointF): Boolean {
        // throttle events: only send updates when there's some movement compared to last update
        // 3 here is arbitrary
        // 抛弃掉从点击到MOVE小于阈值的事件，不在move阶段进行处理
        if (PointF(lastPos.x - p.x, lastPos.y - p.y).length() < trigger / 3)
            return false
        lastPos.set(p)

        // 计算从DOWN到当前MOVE事件的偏移量，以及dr是相对于屏幕尺寸的百分比-小数
        val dx = p.x - initialPos.x
        val dy = p.y - initialPos.y
        val dr = if (stateDirection == 0) (dx / width) else (-dy / height)

        when (state) {
            State.Up -> {}
            State.Down -> {
                // we might get into one of Control states if user moves enough
                // 根据偏移量确认是横向还是纵向滑动
                if (abs(dx) > trigger) {
                    state = gestureHorizontal
                    stateDirection = 0
                } else if (abs(dy) > trigger) {
                    state = if (initialPos.x > width / 2) gestureVertRight else gestureVertLeft
                    stateDirection = 1
                }
                // send Init so that it has a chance to cache values before we start modifying them
                if (state != State.Down)
                    sendPropertyChange(PropertyChange.Init, 0f)
            }

            State.ControlSeek ->
                sendPropertyChange(PropertyChange.Seek, CONTROL_SEEK_MAX * dr)

            State.ControlVolume ->
                sendPropertyChange(PropertyChange.Volume, CONTROL_VOLUME_MAX * dr)

            State.ControlBright ->
                sendPropertyChange(PropertyChange.Bright, CONTROL_BRIGHT_MAX * dr)
        }
        return state != State.Up && state != State.Down
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0f || height == 0f) {
            return false
        }
        var gestureHandled = false
        val point = PointF(event.x, event.y)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 手势事件从down开始
                // dead zone on top/bottom
                // 限制上下 DEAD_ZONE 百分比的区域是不可触摸区域
                if (event.y < height * DEAD_ZONE / 100 || event.y > height * (100 - DEAD_ZONE) / 100)
                    return false
                processTap(point)
                initialPos = point
                lastPos.set(initialPos)
                state = State.Down
                // always return true on ACTION_DOWN to continue receiving events
                // 如果想继续接收后续手势事件，move必须返回true
                gestureHandled = true
            }

            MotionEvent.ACTION_MOVE -> {
                // 手势事件up/down中间是move
                gestureHandled = processMovement(point)
            }

            MotionEvent.ACTION_UP -> {
                // 手势事件至up结束
                gestureHandled = processMovement(point) or processTap(point)
                if (state != State.Down)
                    sendPropertyChange(PropertyChange.Finalize, 0f)
                state = State.Up
                return gestureHandled
            }
        }
        return gestureHandled
    }

    private fun sendPropertyChange(p: PropertyChange, diff: Float) {
        observer.onPropertyChange(p, diff)
    }

    private enum class State {
        Up,
        Down,
        ControlSeek,
        ControlVolume,
        ControlBright,
    }


    companion object {
        // do not trigger on X% of screen top/bottom
        // this is so that user can open android status bar
        // 限制屏幕上下的不可点击区域，用以方便用户打开Android的状态栏
        private const val DEAD_ZONE = 5

        // maximum duration between taps (ms) for a double tap to count
        // 双击事件判断的最大间隔
        private const val TAP_DURATION = 300L

        // ratio for trigger, 1/Xth of minimum dimension
        // for tap gestures this is the distance that must *not* be moved for it to trigger
        private const val TRIGGER_RATE = 30

        // full sweep from left side to right side is 2:30
        private const val CONTROL_SEEK_MAX = 150f

        // same as below, we rescale it inside MPVActivity
        private const val CONTROL_VOLUME_MAX = 1.5f

        // brightness is scaled 0..1; max's not 1f so that user does not have to start from the bottom
        // if they want to go from none to full brightness
        private const val CONTROL_BRIGHT_MAX = 1.5f
    }
}