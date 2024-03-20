package io.viper.android.mpv.player

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class MainScreenFragment : Fragment(R.layout.fragment_main_screen) {

    private lateinit var mDocumentTreeChooser: ActivityResultLauncher<Uri?> // 不能优化成 by lazy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDocumentTreeChooser =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
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
        view.findViewById<Button>(R.id.urlBtn).setOnClickListener { }
        view.findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            (activity as MainActivity).switchFragment(NavigationPath.SETTINGS)
        }
    }
}