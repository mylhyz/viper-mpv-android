package io.viper.android.mpv.dialog

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import io.viper.android.mpv.core.Player
import io.viper.android.mpv.view.R
import io.viper.android.mpv.view.databinding.DialogTrackBinding

internal typealias Listener = (Player.Track, Boolean) -> Unit

class SubTrackDialog(private val mPlayer: Player) {

    companion object {
        const val TRACK_TYPE = "sub"
    }

    private lateinit var mBinding: DialogTrackBinding

    private var tracks = listOf<Player.Track>()
    private var secondary = false

    // ID of the selected primary track
    private var selectedMpvId = -1

    // ID of the selected secondary track
    private var selectedMpvId2 = -1

    var listener: Listener? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        mBinding = DialogTrackBinding.inflate(layoutInflater)

        mBinding.primaryBtn.setOnClickListener {
            secondary = false
            refresh()
        }
        mBinding.secondaryBtn.setOnClickListener {
            secondary = true
            refresh()
        }

        // Set up recycler view
        mBinding.list.adapter = CustomAdapter(this)
        refresh()

        return mBinding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        tracks = mPlayer.tracks.getValue(TRACK_TYPE)
        selectedMpvId = mPlayer.sid
        selectedMpvId2 = mPlayer.secondarySid

        // show primary/secondary toggle if applicable
        if (secondary || selectedMpvId2 != -1 || tracks.size > 2) {
            mBinding.buttonRow.visibility = View.VISIBLE
            mBinding.divider.visibility = View.VISIBLE
        } else {
            mBinding.buttonRow.visibility = View.GONE
            mBinding.divider.visibility = View.GONE
        }

        /* FIXME?: there's some kind of layout bug on every second call here where a bunch of
            empty space (dis-)appears at the bottom, but I only have this in the emulator (api 33)
            but not on my phone (api 30) */
        mBinding.list.adapter!!.notifyDataSetChanged()
        val index =
            tracks.indexOfFirst { it.mpvId == if (secondary) selectedMpvId2 else selectedMpvId }
        mBinding.list.scrollToPosition(index)
    }

    private fun clickItem(position: Int) {
        val item = tracks[position]
        listener?.invoke(item, secondary)
    }

    private class CustomAdapter(private val parent: SubTrackDialog) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        class ViewHolder(private val parent: SubTrackDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: CheckedTextView =
                ViewCompat.requireViewById(view, android.R.id.text1)

            init {
                view.setOnClickListener {
                    parent.clickItem(adapterPosition)
                }
            }

            fun bind(track: Player.Track, checked: Boolean, disabled: Boolean) {
                with(textView) {
                    text = track.name
                    isChecked = checked
                    isEnabled = !disabled
                }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.dialog_track_item, viewGroup, false)
            return ViewHolder(parent, view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val track = parent.tracks[position]
            var (checked, disabled) = if (parent.secondary) {
                Pair(track.mpvId == parent.selectedMpvId2, track.mpvId == parent.selectedMpvId)
            } else {
                Pair(track.mpvId == parent.selectedMpvId, track.mpvId == parent.selectedMpvId2)
            }
            // selectedMpvId2 may be -1 but this special entry is for disabling a track
            if (track.mpvId == -1)
                disabled = false
            viewHolder.bind(track, checked, disabled)
        }

        override fun getItemCount() = parent.tracks.size
    }
}