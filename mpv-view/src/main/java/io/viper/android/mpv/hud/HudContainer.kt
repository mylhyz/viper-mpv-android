package io.viper.android.mpv.hud

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import io.viper.android.mpv.IPlayerHandler
import io.viper.android.mpv.NativeLibrary
import io.viper.android.mpv.core.Player
import io.viper.android.mpv.getString
import io.viper.android.mpv.view.R
import io.viper.android.mpv.view.databinding.HudContainerBinding

typealias StateRestoreCallback = () -> Unit

class HudContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mBinding: HudContainerBinding =
        HudContainerBinding.inflate(LayoutInflater.from(context), this)

    var mPlayer: Player? = null
    var mPlayerHandler: IPlayerHandler? = null

    init {
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
    }

    private fun requirePlayer(): Player {
        return mPlayer!!
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
        // TODO
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
//        updatePiPParams(true)
//        enterPictureInPictureMode()
    }

    private fun openTopMenu() {
        // TODO
//        val restoreState = pauseForDialog()
//
//        fun addExternalThing(cmd: String, result: Int, data: Intent?) {
//            if (result != AppCompatActivity.RESULT_OK) return
//            // file picker may return a content URI or a bare file path
//            val path = data!!.getStringExtra("path")!!
//            val path2 = if (path.startsWith("content://")) openContentFd(Uri.parse(path))
//            else path
//            MPVLib.command(arrayOf(cmd, path2, "cached"))
//        }
//
//        /******/
//        val hiddenButtons = mutableSetOf<Int>()
//        val buttons: MutableList<MenuItem> = mutableListOf(MenuItem(R.id.audioBtn) {
//            openFilePickerFor(
//                RCODE_EXTERNAL_AUDIO, R.string.open_external_audio
//            ) { result, data ->
//                addExternalThing("audio-add", result, data)
//                restoreState()
//            }; false
//        },
//            MenuItem(R.id.subBtn) {
//                openFilePickerFor(RCODE_EXTERNAL_SUB, R.string.open_external_sub) { result, data ->
//                    addExternalThing("sub-add", result, data)
//                    restoreState()
//                }; false
//            },
//            MenuItem(R.id.playlistBtn) {
//                openPlaylistMenu(restoreState); false
//            },
//            MenuItem(R.id.backgroundBtn) {
//                backgroundPlayMode = "always"
//                player.paused = false
//                moveTaskToBack(true)
//                false
//            },
//            MenuItem(R.id.chapterBtn) {
//                val chapters = player.loadChapters()
//                if (chapters.isEmpty()) return@MenuItem true
//                val chapterArray = chapters.map {
//                    val timecode = Utils.prettyTime(it.time.roundToInt())
//                    if (!it.title.isNullOrEmpty()) getString(
//                        R.string.ui_chapter,
//                        it.title,
//                        timecode
//                    )
//                    else getString(R.string.ui_chapter_fallback, it.index + 1, timecode)
//                }.toTypedArray()
//                val selectedIndex = MPVLib.getPropertyInt("chapter") ?: 0
//                with(AlertDialog.Builder(this)) {
//                    setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
//                        MPVLib.setPropertyInt("chapter", chapters[item].index)
//                        dialog.dismiss()
//                    }
//                    setOnDismissListener { restoreState() }
//                    create().show()
//                }; false
//            },
//            MenuItem(R.id.chapterPrev) {
//                MPVLib.command(arrayOf("add", "chapter", "-1")); true
//            },
//            MenuItem(R.id.chapterNext) {
//                MPVLib.command(arrayOf("add", "chapter", "1")); true
//            },
//            MenuItem(R.id.advancedBtn) { openAdvancedMenu(restoreState); false },
//            MenuItem(R.id.orientationBtn) { this.cycleOrientation(); true })
//
//        if (player.aid == -1) hiddenButtons.add(R.id.backgroundBtn)
//        if (MPVLib.getPropertyInt("chapter-list/count") ?: 0 == 0) hiddenButtons.add(R.id.rowChapter)
//        if (autoRotationMode == "auto") hiddenButtons.add(R.id.orientationBtn)
//        /******/
//
//        genericMenu(R.layout.dialog_top_menu, buttons, hiddenButtons, restoreState)
    }

    private fun pickAudio() =
        selectTrack("audio", { requirePlayer().aid }, { requirePlayer().aid = it })

    private fun pickSpeed() {
        // TODO: replace this with SliderPickerDialog
//        val picker = SpeedPickerDialog()
//
//        val restore = pauseForDialog()
//        genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
//            updateSpeedButton()
//            restore()
//        }
    }

    private fun pickSub() {
        // TODO
//        val restore = pauseForDialog()
//        val impl = SubTrackDialog(player)
//        lateinit var dialog: AlertDialog
//        impl.listener = { it, secondary ->
//            if (secondary) player.secondarySid = it.mpvId
//            else player.sid = it.mpvId
//            dialog.dismiss()
//            trackSwitchNotification { TrackData(it.mpvId, SubTrackDialog.TRACK_TYPE) }
//        }
//
//        dialog = with(AlertDialog.Builder(this)) {
//            setView(impl.buildView(layoutInflater))
//            setOnDismissListener { restore() }
//            create()
//        }
//        dialog.show()
    }

    private fun pauseForDialog(): StateRestoreCallback {
        // TODO
//        val useKeepOpen = when (noUIPauseMode) {
//            "always" -> true
//            "audio-only" -> isPlayingAudioOnly()
//            else -> false // "never"
//        }
//        if (useKeepOpen) {
//            // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
//            val oldValue = MPVLib.getPropertyString("keep-open")
//            MPVLib.setPropertyBoolean("keep-open", true)
//            return {
//                MPVLib.setPropertyString("keep-open", oldValue)
//            }
//        }
//
//        // Pause playback during UI dialogs
//        val wasPlayerPaused = player.paused ?: true
//        player.paused = true
//        return {
//            if (!wasPlayerPaused) player.paused = false
//        }

        return {}
    }

    private fun openPlaylistMenu(restore: StateRestoreCallback) {
        // TODO
//        val impl = PlaylistDialog(player)
//        lateinit var dialog: AlertDialog
//
//        impl.listeners = object : PlaylistDialog.Listeners {
//            private fun openFilePicker(skip: Int) {
//                openFilePickerFor(RCODE_LOAD_FILE, "", skip) { result, data ->
//                    if (result == AppCompatActivity.RESULT_OK) {
//                        val path = data!!.getStringExtra("path")
//                        MPVLib.command(arrayOf("loadfile", path, "append"))
//                        impl.refresh()
//                    }
//                }
//            }
//
//            override fun pickFile() = openFilePicker(FilePickerActivity.FILE_PICKER)
//
//            override fun openUrl() {
//                val helper = Utils.OpenUrlDialog(this@MPVActivity)
//                with(helper) {
//                    builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
//                        MPVLib.command(arrayOf("loadfile", helper.text, "append"))
//                        impl.refresh()
//                    }
//                    builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
//                    create().show()
//                }
//            }
//
//            override fun onItemPicked(item: MPVView.PlaylistItem) {
//                MPVLib.setPropertyInt("playlist-pos", item.index)
//                dialog.dismiss()
//            }
//        }
//
//        dialog = with(AlertDialog.Builder(this)) {
//            setView(impl.buildView(layoutInflater))
//            setOnDismissListener { restore() }
//            create()
//        }
//        dialog.show()
    }

    private fun pickDecoder() {
        val restore = pauseForDialog()

        val items = mutableListOf(
            Pair("HW (mediacodec-copy)", "mediacodec-copy"), Pair("SW", "no")
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) items.add(
            0,
            Pair("HW+ (mediacodec)", "mediacodec")
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
                tracks.map { it.name }.toTypedArray(),
                selectedIndex
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
}