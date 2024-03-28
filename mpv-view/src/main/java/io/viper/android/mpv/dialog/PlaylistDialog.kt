package io.viper.android.mpv.dialog

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.viper.android.mpv.NativeLibrary
import io.viper.android.mpv.core.Player
import io.viper.android.mpv.fileBasename
import io.viper.android.mpv.view.R
import io.viper.android.mpv.view.databinding.DialogPlaylistBinding

class PlaylistDialog(private val mPlayer: Player) {

    private lateinit var mBinding: DialogPlaylistBinding

    private var playlist = listOf<Player.PlaylistItem>()
    private var selectedIndex = -1

    interface Listener {
        fun pickFile()
        fun openUrl()
        fun onItemPicked(item: Player.PlaylistItem)
    }

    var listener: Listener? = null


    fun buildView(layoutInflater: LayoutInflater): View {
        mBinding = DialogPlaylistBinding.inflate(layoutInflater)

        // Set up recycler view
        mBinding.list.adapter = CustomAdapter(this)
        mBinding.list.setHasFixedSize(true)
        refresh()

        mBinding.fileBtn.setOnClickListener { listener?.pickFile() }
        mBinding.urlBtn.setOnClickListener { listener?.openUrl() }

        mBinding.shuffleBtn.setOnClickListener {
            mPlayer.changeShuffle(true)
            refresh()
        }
        mBinding.repeatBtn.setOnClickListener {
            mPlayer.cycleRepeat()
            refresh()
        }

        return mBinding.root
    }

    fun refresh() {
        selectedIndex = NativeLibrary.getPropertyInt("playlist-pos") ?: -1
        playlist = mPlayer.loadPlaylist()
        Log.v(TAG, "PlaylistDialog: loaded ${playlist.size} items")
        mBinding.list.adapter!!.notifyDataSetChanged()
        mBinding.list.scrollToPosition(playlist.indexOfFirst { it.index == selectedIndex })

        val accent = ContextCompat.getColor(mBinding.root.context, R.color.accent)
        val disabled = ContextCompat.getColor(mBinding.root.context, R.color.alpha_disabled)
        //
        val shuffleState = mPlayer.getShuffle()
        mBinding.shuffleBtn.apply {
            isEnabled = playlist.size > 1
            imageTintList = if (isEnabled)
                if (shuffleState) ColorStateList.valueOf(accent) else null
            else
                ColorStateList.valueOf(disabled)
        }
        val repeatState = mPlayer.getRepeat()
        mBinding.repeatBtn.apply {
            imageTintList = if (repeatState > 0) ColorStateList.valueOf(accent) else null
            setImageResource(if (repeatState == 2) R.drawable.ic_repeat_one_24dp else R.drawable.ic_repeat_24dp)
        }
    }

    private fun clickItem(position: Int) {
        val item = playlist[position]
        listener?.onItemPicked(item)
    }

    class CustomAdapter(private val parent: PlaylistDialog) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        class ViewHolder(private val parent: PlaylistDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: TextView = view.findViewById(android.R.id.text1)

            init {
                view.setOnClickListener {
                    parent.clickItem(adapterPosition)
                }
            }

            fun bind(item: Player.PlaylistItem, selected: Boolean) {
                textView.text = item.title ?: fileBasename(item.filename)
                textView.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.dialog_playlist_item, viewGroup, false)
            return ViewHolder(parent, view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val item = parent.playlist[position]
            viewHolder.bind(item, item.index == parent.selectedIndex)
        }

        override fun getItemCount() = parent.playlist.size
    }

    companion object {
        private const val TAG = ""
    }
}