package io.viper.android.mpv.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity(R.layout.activity_frag_container) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(supportFragmentManager.beginTransaction()) {
            add(R.id.fragment_container_view, SettingsFragment())
            commit()
        }
    }
}