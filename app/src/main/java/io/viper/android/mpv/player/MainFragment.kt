package io.viper.android.mpv.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var mDocumentTreeChooser: ActivityResultLauncher<Uri?> // 不能优化成 by lazy
    private lateinit var mPlayerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDocumentTreeChooser =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
                it?.let { root ->
                    requireContext().contentResolver.takePersistableUriPermission(
                        root, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    // TODO 打开文件夹
                }
            }
        mPlayerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                // we don't care about the result but remember that we've been here
                // TODO
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.docBtn).setOnClickListener {
            try {
                mDocumentTreeChooser.launch(null)
            } catch (e: ActivityNotFoundException) {
                it.isEnabled = false
            }
        }
        view.findViewById<Button>(R.id.urlBtn).setOnClickListener {
            val helper = OpenUrlDialog(requireContext())
            with(helper) {
                builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                    playFile(helper.text)
                }
                builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                create().show()
            }
        }
        view.findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    private fun playFile(filepath: String) {
        val intent: Intent
        if (filepath.startsWith("content://")) {
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(filepath))
        } else {
            intent = Intent()
            intent.putExtra("filepath", filepath)
        }
        intent.setClass(requireContext(), PlayerActivity::class.java)
        mPlayerLauncher.launch(intent)
    }
}