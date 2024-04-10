package io.viper.android.mpv.hud

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceManager
import io.viper.android.mpv.IPlayerHandler
import io.viper.android.mpv.NativeLibrary
import io.viper.android.mpv.OpenUrlDialog
import io.viper.android.mpv.convertDp
import io.viper.android.mpv.core.Player
import io.viper.android.mpv.dialog.DecimalPickerDialog
import io.viper.android.mpv.dialog.IPickerDialog
import io.viper.android.mpv.dialog.PlaylistDialog
import io.viper.android.mpv.dialog.SliderPickerDialog
import io.viper.android.mpv.dialog.SpeedPickerDialog
import io.viper.android.mpv.dialog.SubTrackDialog
import io.viper.android.mpv.getString
import io.viper.android.mpv.prettyTime
import io.viper.android.mpv.view.R
import io.viper.android.mpv.view.databinding.HudContainerBinding
import io.viper.android.mpv.viewGroupMove
import io.viper.android.mpv.viewGroupReorder
import io.viper.android.mpv.visibleChildren
import kotlin.math.roundToInt

typealias StateRestoreCallback = () -> Unit

class HudContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NativeLibrary.EventObserver {

    private val mBinding: HudContainerBinding =
        HudContainerBinding.inflate(LayoutInflater.from(context), this)

    /* Settings */
    private var statsFPS = false

    //    private var statsLuaMode = 0 // ==0 disabled, >0 page number
    var backgroundPlayMode = ""
    private var noUIPauseMode = ""

    var shouldSavePosition = false
    private var autoRotationMode = ""
    private var controlsAtBottom = true
    private var showMediaTitle = false
//    private var ignoreAudioFocus = false
//    private var smoothSeekGesture = false

    /* gesture */
    private var userIsOperatingSeekbar = false

    /* internal props */
    var mPlayer: Player? = null
    var mPlayerHandler: IPlayerHandler? = null

    init {
        syncSettings()
        initWithListener()
    }

    data class TrackData(val trackId: Int, val trackType: String)

    private fun trackSwitchNotification(f: () -> TrackData) {
        val (trackId, trackType) = f()
        val trackPrefix = when (trackType) {
            "audio" -> getString(R.string.track_audio)
            "sub" -> getString(R.string.track_subs)
            "video" -> "Video"
            else -> "???"
        }

        val msg = if (trackId == -1) {
            "$trackPrefix ${getString(R.string.track_off)}"
        } else {
            val trackName =
                requirePlayer().tracks[trackType]?.firstOrNull { it.mpvId == trackId }?.name
                    ?: "???"
            "$trackPrefix $trackName"
        }
        mPlayerHandler?.showToast(msg, true)
    }

    data class MenuItem(@IdRes val idRes: Int, val handler: () -> Boolean)

    private fun genericMenu(
        @LayoutRes layoutRes: Int,
        buttons: List<MenuItem>,
        hiddenButtons: Set<Int>,
        restoreState: StateRestoreCallback
    ) {
        lateinit var dialog: AlertDialog
        val dialogView = LayoutInflater.from(context).inflate(layoutRes, null)

        for (button in buttons) {
            val buttonView = dialogView.findViewById<Button>(button.idRes)
            buttonView.setOnClickListener {
                val ret = button.handler()
                if (ret) // restore state immediately
                    restoreState()
                dialog.dismiss()
            }
        }

        hiddenButtons.forEach { dialogView.findViewById<View>(it).visibility = View.GONE }

        if (visibleChildren(dialogView) == 0) {
            Log.w(TAG, "Not showing menu because it would be empty")
            restoreState()
            return
        }

        with(AlertDialog.Builder(context)) {
            setView(dialogView)
            setOnCancelListener { restoreState() }
            dialog = create()
        }
        dialog.show()
    }

    private fun initWithListener() {
        with(mBinding) {
            prevBtn.setOnClickListener { playlistPrev() }
            nextBtn.setOnClickListener { playlistNext() }
            cycleAudioBtn.setOnClickListener { cycleAudio() }
            cycleSubsBtn.setOnClickListener { cycleSub() }
            playBtn.setOnClickListener { requirePlayer().cyclePause() }
            cycleDecoderBtn.setOnClickListener { requirePlayer().cycleHwdec() }
            cycleSpeedBtn.setOnClickListener { cycleSpeed() }
            topLockBtn.setOnClickListener { lockUI() }
            topPiPBtn.setOnClickListener { goIntoPiP() }
            topMenuBtn.setOnClickListener { openTopMenu() }
            unlockBtn.setOnClickListener { unlockUI() }

            cycleAudioBtn.setOnLongClickListener { pickAudio(); true }
            cycleSpeedBtn.setOnLongClickListener { pickSpeed(); true }
            cycleSubsBtn.setOnLongClickListener { pickSub(); true }
            prevBtn.setOnLongClickListener { openPlaylistMenu(pauseForDialog()); true }
            nextBtn.setOnLongClickListener { openPlaylistMenu(pauseForDialog()); true }
            cycleDecoderBtn.setOnLongClickListener { pickDecoder(); true }
        }

        // 将所有的Control置于系统的导航栏等之外，避免重合
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.outside) { _, windowInsets ->
            // guidance: https://medium.com/androiddevelopers/gesture-navigation-handling-visual-overlaps-4aed565c134c
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val insets2 = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            mBinding.outside.updateLayoutParams<MarginLayoutParams> {
                // avoid system bars and cutout
                leftMargin = Math.max(insets.left, insets2.left)
                topMargin = Math.max(insets.top, insets2.top)
                bottomMargin = Math.max(insets.bottom, insets2.bottom)
                rightMargin = Math.max(insets.right, insets2.right)
            }
            WindowInsetsCompat.CONSUMED
        }

        // 系统支持判断
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) mBinding.topPiPBtn.visibility =
            View.GONE
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) mBinding.topLockBtn.visibility =
            View.GONE

        if (showMediaTitle) mBinding.controlsTitleGroup.visibility = View.VISIBLE
    }

    private fun syncSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val getString: (String, Int) -> String = { key, defaultRes ->
            prefs.getString(key, resources.getString(defaultRes))!!
        }

        val statsMode = prefs.getString("stats_mode", "") ?: ""
        this.statsFPS = statsMode == "native_fps"
//        this.statsLuaMode = if (statsMode.startsWith("lua")) statsMode.removePrefix("lua").toInt()
//        else 0
        this.backgroundPlayMode =
            getString("background_play", R.string.pref_background_play_default)
        this.noUIPauseMode = getString("no_ui_pause", R.string.pref_no_ui_pause_default)
        this.shouldSavePosition = prefs.getBoolean("save_position", false)
        this.autoRotationMode = getString("auto_rotation", R.string.pref_auto_rotation_default)
        this.controlsAtBottom = prefs.getBoolean("bottom_controls", true)
        this.showMediaTitle = prefs.getBoolean("display_media_title", false)
//        this.ignoreAudioFocus = prefs.getBoolean("ignore_audio_focus", false)
//        this.smoothSeekGesture = prefs.getBoolean("seek_gesture_smooth", false)
    }

    private fun requirePlayer(): Player {
        return mPlayer!!
    }

    private fun genericPickerDialog(
        picker: IPickerDialog,
        @StringRes titleRes: Int,
        property: String,
        restoreState: StateRestoreCallback
    ) {
        val dialog = with(AlertDialog.Builder(context)) {
            setTitle(titleRes)
            setView(picker.buildView(LayoutInflater.from(context)))
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                picker.number?.let {
                    if (picker.isInteger()) NativeLibrary.setPropertyInt(property, it.toInt())
                    else NativeLibrary.setPropertyDouble(property, it)
                }
            }
            setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
            setOnDismissListener { restoreState() }
            create()
        }

        picker.number = NativeLibrary.getPropertyDouble(property)
        dialog.show()
    }

    // actions
    private fun playlistPrev() = NativeLibrary.command(arrayOf("playlist-prev"))
    private fun playlistNext() = NativeLibrary.command(arrayOf("playlist-next"))

    private fun cycleAudio() = trackSwitchNotification {
        requirePlayer().cycleAudio(); TrackData(requirePlayer().aid, "audio")
    }

    private fun cycleSub() = trackSwitchNotification {
        requirePlayer().cycleSub(); TrackData(requirePlayer().sid, "sub")
    }

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = requirePlayer().playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        requirePlayer().playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    private fun cycleOrientation() {
        mPlayerHandler?.cycleOrientation()
    }

    private fun lockUI() {
        // TODO
//        lockedUI = true
//        hideControlsDelayed()
    }

    private fun unlockUI() {
        // TODO
//        binding.unlockBtn.visibility = View.GONE
//        lockedUI = false
//        showControls()
    }

    private fun goIntoPiP() {
        mPlayerHandler?.getIntoPictureInPictureMode()
    }

    private fun openAdvancedMenu(restoreState: StateRestoreCallback) {
        /******/
        val hiddenButtons = mutableSetOf<Int>()
        val buttons: MutableList<MenuItem> = mutableListOf(
            MenuItem(R.id.subSeekPrev) {
                NativeLibrary.command(arrayOf("sub-seek", "-1")); true
            },
            MenuItem(R.id.subSeekNext) {
                NativeLibrary.command(arrayOf("sub-seek", "1")); true
            },
            MenuItem(R.id.statsBtn) {
                NativeLibrary.command(arrayOf("script-binding", "stats/display-stats-toggle")); true
            },
            MenuItem(R.id.aspectBtn) {
                val ratios = resources.getStringArray(R.array.aspect_ratios)
                with(AlertDialog.Builder(context)) {
                    setItems(R.array.aspect_ratio_names) { dialog, item ->
                        if (ratios[item] == "panscan") {
                            NativeLibrary.setPropertyString("video-aspect-override", "-1")
                            NativeLibrary.setPropertyDouble("panscan", 1.0)
                        } else {
                            NativeLibrary.setPropertyString("video-aspect-override", ratios[item])
                            NativeLibrary.setPropertyDouble("panscan", 0.0)
                        }
                        dialog.dismiss()
                    }
                    setOnDismissListener { restoreState() }
                    create().show()
                }; false
            },
        )

        val statsButtons = arrayOf(R.id.statsBtn1, R.id.statsBtn2, R.id.statsBtn3)
        for (i in 1..3) {
            buttons.add(MenuItem(statsButtons[i - 1]) {
                NativeLibrary.command(arrayOf("script-binding", "stats/display-page-$i")); true
            })
        }

        // contrast, brightness and others get a -100 to 100 slider
        val basicIds =
            arrayOf(R.id.contrastBtn, R.id.brightnessBtn, R.id.gammaBtn, R.id.saturationBtn)
        val basicProps = arrayOf("contrast", "brightness", "gamma", "saturation")
        val basicTitles = arrayOf(
            R.string.contrast, R.string.video_brightness, R.string.gamma, R.string.saturation
        )
        basicIds.forEachIndexed { index, id ->
            buttons.add(MenuItem(id) {
                val slider = SliderPickerDialog(-100.0, 100.0, 1, R.string.format_fixed_number)
                genericPickerDialog(slider, basicTitles[index], basicProps[index], restoreState)
                false
            })
        }

        // audio / sub delay get a decimal picker
        arrayOf(R.id.audioDelayBtn, R.id.subDelayBtn).forEach { id ->
            val title = if (id == R.id.audioDelayBtn) R.string.audio_delay else R.string.sub_delay
            val prop = if (id == R.id.audioDelayBtn) "audio-delay" else "sub-delay"
            buttons.add(MenuItem(id) {
                val picker = DecimalPickerDialog(-600.0, 600.0)
                genericPickerDialog(picker, title, prop, restoreState)
                false
            })
        }

        if (requirePlayer().vid == -1) hiddenButtons.addAll(
            arrayOf(
                R.id.rowVideo1, R.id.rowVideo2, R.id.aspectBtn
            )
        )
        if (requirePlayer().aid == -1 || requirePlayer().vid == -1) hiddenButtons.add(R.id.audioDelayBtn)
        if (requirePlayer().sid == -1) hiddenButtons.addAll(
            arrayOf(
                R.id.subDelayBtn, R.id.rowSubSeek
            )
        )
        /******/

        genericMenu(R.layout.dialog_advanced_menu, buttons, hiddenButtons, restoreState)
    }

    private fun openTopMenu() {
        // TODO
        val restoreState = pauseForDialog()

        fun addExternalThing(cmd: String, uri: Uri?) {
            if (uri == null) return
            val data = uri.toString()
            // file picker may return a content URI or a bare file path
            val path = data
            val path2 = if (path.startsWith("content://")) mPlayerHandler?.openContentFd(uri)
            else path
            NativeLibrary.command(arrayOf(cmd, path2, "cached"))
        }

        /******/
        val hiddenButtons = mutableSetOf<Int>()
        val buttons: MutableList<MenuItem> = mutableListOf(MenuItem(R.id.audioBtn) {
            mPlayerHandler?.openFilePickerFor(
            ) { uri ->
                addExternalThing("audio-add", uri)
                restoreState()
            }; false
        },
            MenuItem(R.id.subBtn) {
                mPlayerHandler?.openFilePickerFor(
                ) { uri ->
                    addExternalThing("sub-add", uri)
                    restoreState()
                }; false
            },
            MenuItem(R.id.playlistBtn) {
                openPlaylistMenu(restoreState); false
            },
            MenuItem(R.id.backgroundBtn) {
                backgroundPlayMode = "always"
                requirePlayer().paused = false
                mPlayerHandler?.activityMoveTaskToBack(true)
                false
            },
            MenuItem(R.id.chapterBtn) {
                val chapters = requirePlayer().loadChapters()
                if (chapters.isEmpty()) return@MenuItem true
                val chapterArray = chapters.map {
                    val timecode = prettyTime(it.time.roundToInt())
                    if (!it.title.isNullOrEmpty()) getString(
                        R.string.ui_chapter, it.title, timecode
                    )
                    else getString(R.string.ui_chapter_fallback, it.index + 1, timecode)
                }.toTypedArray()
                val selectedIndex = NativeLibrary.getPropertyInt("chapter") ?: 0
                with(AlertDialog.Builder(context)) {
                    setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
                        NativeLibrary.setPropertyInt("chapter", chapters[item].index)
                        dialog.dismiss()
                    }
                    setOnDismissListener { restoreState() }
                    create().show()
                }; false
            },
            MenuItem(R.id.chapterPrev) {
                NativeLibrary.command(arrayOf("add", "chapter", "-1")); true
            },
            MenuItem(R.id.chapterNext) {
                NativeLibrary.command(arrayOf("add", "chapter", "1")); true
            },
            MenuItem(R.id.advancedBtn) { openAdvancedMenu(restoreState); false },
            MenuItem(R.id.orientationBtn) { this.cycleOrientation(); true })

        if (requirePlayer().aid == -1) hiddenButtons.add(R.id.backgroundBtn)
        if (NativeLibrary.getPropertyInt("chapter-list/count") ?: 0 == 0) hiddenButtons.add(R.id.rowChapter)
        if (autoRotationMode == "auto") hiddenButtons.add(R.id.orientationBtn)
        /******/

        genericMenu(R.layout.dialog_top_menu, buttons, hiddenButtons, restoreState)
    }

    private fun pickAudio() =
        selectTrack("audio", { requirePlayer().aid }, { requirePlayer().aid = it })

    private fun pickSpeed() {
        // TODO: replace this with SliderPickerDialog
        val picker = SpeedPickerDialog()

        val restore = pauseForDialog()
        genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
            updateSpeedButton()
            restore()
        }
    }

    private fun pickSub() {
        val restore = pauseForDialog()
        val impl = SubTrackDialog(requirePlayer())
        lateinit var dialog: AlertDialog
        impl.listener = { it, secondary ->
            if (secondary) requirePlayer().secondarySid = it.mpvId
            else requirePlayer().sid = it.mpvId
            dialog.dismiss()
            trackSwitchNotification { TrackData(it.mpvId, SubTrackDialog.TRACK_TYPE) }
        }

        dialog = with(AlertDialog.Builder(context)) {
            setView(impl.buildView(LayoutInflater.from(context)))
            setOnDismissListener { restore() }
            create()
        }
        dialog.show()
    }

    private fun pauseForDialog(): StateRestoreCallback {
        val useKeepOpen = when (noUIPauseMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false // "never"
        }
        if (useKeepOpen) {
            // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
            val oldValue = NativeLibrary.getPropertyString("keep-open")!!
            NativeLibrary.setPropertyBoolean("keep-open", true)
            return {
                NativeLibrary.setPropertyString("keep-open", oldValue)
            }
        }

        // Pause playback during UI dialogs
        val wasPlayerPaused = requirePlayer().paused ?: true
        requirePlayer().paused = true
        return {
            if (!wasPlayerPaused) requirePlayer().paused = false
        }
    }

    private fun openPlaylistMenu(restore: StateRestoreCallback) {
        val impl = PlaylistDialog(mPlayer!!)
        lateinit var dialog: AlertDialog

        impl.listener = object : PlaylistDialog.Listener {
            private fun openFilePicker() {
                mPlayerHandler?.openFilePickerFor() { uri ->
                    uri?.let {
                        val path = it.toString()
                        NativeLibrary.command(arrayOf("loadfile", path, "append"))
                        impl.refresh()
                    }
                }
            }

            override fun pickFile() = openFilePicker()

            override fun openUrl() {
                val helper = OpenUrlDialog(context)
                with(helper) {
                    builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                        NativeLibrary.command(arrayOf("loadfile", helper.text, "append"))
                        impl.refresh()
                    }
                    builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                    create().show()
                }
            }

            override fun onItemPicked(item: Player.PlaylistItem) {
                NativeLibrary.setPropertyInt("playlist-pos", item.index)
                dialog.dismiss()
            }
        }

        dialog = with(AlertDialog.Builder(context)) {
            setView(impl.buildView(LayoutInflater.from(context)))
            setOnDismissListener { restore() }
            create()
        }
        dialog.show()
    }

    private fun pickDecoder() {
        val restore = pauseForDialog()

        val items = mutableListOf(
            Pair("HW (mediacodec-copy)", "mediacodec-copy"), Pair("SW", "no")
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) items.add(
            0, Pair("HW+ (mediacodec)", "mediacodec")
        )
        val hwdecActive = requirePlayer().hwdecActive
        val selectedIndex = items.indexOfFirst { it.second == hwdecActive }
        with(AlertDialog.Builder(context)) {
            setSingleChoiceItems(
                items.map { it.first }.toTypedArray(), selectedIndex
            ) { dialog, idx ->
                NativeLibrary.setPropertyString("hwdec", items[idx].second)
                dialog.dismiss()
            }
            setOnDismissListener { restore() }
            create().show()
        }
    }


    private fun selectTrack(type: String, get: () -> Int, set: (Int) -> Unit) {
        val tracks = requirePlayer().tracks.getValue(type)
        val selectedMpvId = get()
        val selectedIndex = tracks.indexOfFirst { it.mpvId == selectedMpvId }
        val restore = pauseForDialog()

        with(AlertDialog.Builder(context)) {
            setSingleChoiceItems(
                tracks.map { it.name }.toTypedArray(), selectedIndex
            ) { dialog, item ->
                val trackId = tracks[item].mpvId

                set(trackId)
                dialog.dismiss()
                trackSwitchNotification { TrackData(trackId, type) }
            }
            setOnDismissListener { restore() }
            create().show()
        }
    }

    // UI Updated

    private var useAudioUI = false

    private fun updateStats() {
        if (!statsFPS)
            return
        mBinding.statsTextView.text = getString(R.string.ui_fps, requirePlayer().estimatedVfFps)
    }

    private fun updatePlaybackStatus(paused: Boolean) {
        val r = if (paused) R.drawable.ic_play_arrow_black_24dp else R.drawable.ic_pause_black_24dp
        mBinding.playBtn.setImageResource(r)

        mPlayerHandler?.updatePictureInPictureParams()
        mPlayerHandler?.updateKeepScreenOn(paused)
    }

    private fun updatePlaybackDuration(duration: Int) {
        mBinding.playbackDurationTxt.text = prettyTime(duration)
        if (!userIsOperatingSeekbar)
            mBinding.playbackSeekbar.max = duration
    }

    fun updatePlaybackPos(position: Int) {
        mBinding.playbackPositionTxt.text = prettyTime(position)
        if (!userIsOperatingSeekbar)
            mBinding.playbackSeekbar.progress = position

        updateDecoderButton()
        updateSpeedButton()
        updateStats()
    }

    private fun updateSpeedButton() {
        mBinding.cycleSpeedBtn.text = getString(R.string.ui_speed, requirePlayer().playbackSpeed)
    }

    private fun updateDecoderButton() {
        if (mBinding.cycleDecoderBtn.visibility != View.VISIBLE) return
        mBinding.cycleDecoderBtn.text = when (requirePlayer().hwdecActive) {
            "mediacodec" -> "HW+"
            "no" -> "SW"
            else -> "HW"
        }
    }

    private fun updateMetadataDisplay() {
        val psc = requirePlayer().psc
        if (!useAudioUI) {
            if (showMediaTitle) mBinding.fullTitleTextView.text = psc.meta.formatTitle()
        } else {
            mBinding.titleTextView.text = psc.meta.formatTitle()
            mBinding.minorTitleTextView.text = psc.meta.formatArtistAlbum()
        }
    }

    private fun updatePlaylistButtons() {
        val psc = requirePlayer().psc
        val plCount = psc.playlistCount
        val plPos = psc.playlistPos

        if (!useAudioUI && plCount == 1) {
            // use View.GONE so the buttons won't take up any space
            mBinding.prevBtn.visibility = View.GONE
            mBinding.nextBtn.visibility = View.GONE
            return
        }
        mBinding.prevBtn.visibility = View.VISIBLE
        mBinding.nextBtn.visibility = View.VISIBLE

        val g = ContextCompat.getColor(context, R.color.tint_disabled)
        val w = ContextCompat.getColor(context, R.color.tint_normal)
        mBinding.prevBtn.imageTintList = ColorStateList.valueOf(if (plPos == 0) g else w)
        mBinding.nextBtn.imageTintList = ColorStateList.valueOf(if (plPos == plCount - 1) g else w)
    }

    private fun updateAudioUI() {
        val audioButtons = arrayOf(
            R.id.prevBtn, R.id.cycleAudioBtn, R.id.playBtn, R.id.cycleSpeedBtn, R.id.nextBtn
        )
        val videoButtons = arrayOf(
            R.id.cycleAudioBtn,
            R.id.cycleSubsBtn,
            R.id.playBtn,
            R.id.cycleDecoderBtn,
            R.id.cycleSpeedBtn
        )

        val shouldUseAudioUI = isPlayingAudioOnly()
        if (shouldUseAudioUI == useAudioUI) return
        useAudioUI = shouldUseAudioUI
        Log.v(TAG, "Audio UI: $useAudioUI")

        val seekbarGroup = mBinding.controlsSeekbarGroup
        val buttonGroup = mBinding.controlsButtonGroup

        if (useAudioUI) {
            // Move prev/next file from seekbar group to buttons group
            viewGroupMove(seekbarGroup, R.id.prevBtn, buttonGroup, 0)
            viewGroupMove(seekbarGroup, R.id.nextBtn, buttonGroup, -1)

            // Change button layout of buttons group
            viewGroupReorder(buttonGroup, audioButtons)

            // Show song title and more metadata
            mBinding.controlsTitleGroup.visibility = View.VISIBLE
            viewGroupReorder(
                mBinding.controlsTitleGroup, arrayOf(R.id.titleTextView, R.id.minorTitleTextView)
            )
            updateMetadataDisplay()

            showControls()
        } else {
            viewGroupMove(buttonGroup, R.id.prevBtn, seekbarGroup, 0)
            viewGroupMove(buttonGroup, R.id.nextBtn, seekbarGroup, -1)

            viewGroupReorder(buttonGroup, videoButtons)

            // Show title only depending on settings
            if (showMediaTitle) {
                mBinding.controlsTitleGroup.visibility = View.VISIBLE
                viewGroupReorder(mBinding.controlsTitleGroup, arrayOf(R.id.fullTitleTextView))
                updateMetadataDisplay()
            } else {
                mBinding.controlsTitleGroup.visibility = View.GONE
            }

            hideControls() // do NOT use fade runnable
        }

        // Visibility might have changed, so update
        updatePlaylistButtons()
    }

    fun isPlayingAudioOnly(): Boolean {
        if (requirePlayer().aid == -1) return false
        val fmt = NativeLibrary.getPropertyString("video-format")
        return fmt.isNullOrEmpty() || arrayOf("mjpeg", "png", "bmp").indexOf(fmt) != -1
    }

    private fun showControls() {}
    private fun hideControls() {}

    fun resume() {

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        // Adjust control margins
        mBinding.controls.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (!controlsAtBottom) {
                context.convertDp(60f)
            } else {
                0
            }
            leftMargin = if (!controlsAtBottom) {
                context.convertDp(if (isLandscape) 60f else 24f)
            } else {
                0
            }
            rightMargin = leftMargin
        }
    }

    override fun eventProperty(property: String) {
        when (property) {
            "track-list" -> requirePlayer().loadTracks(context)
            "video-params/aspect" -> {
                mPlayerHandler?.updateOrientation()
//                TODO updatePiPParams()
            }

            "video-format" -> updateAudioUI()
            "hwdec-current" -> updateDecoderButton()
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> updatePlaybackPos(value.toInt())
            "duration" -> updatePlaybackDuration(value.toInt())
            "playlist-pos", "playlist-count" -> updatePlaylistButtons()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> updatePlaybackStatus(value)
        }
    }

    override fun eventProperty(property: String, value: String) {

    }

    override fun event(evtId: Int) {
        when (evtId) {
            NativeLibrary.EventId.MPV_EVENT_PLAYBACK_RESTART -> updatePlaybackStatus(requirePlayer().paused!!)
        }
    }

    companion object {
        private const val TAG = "mpv.HudContainer"
    }
}