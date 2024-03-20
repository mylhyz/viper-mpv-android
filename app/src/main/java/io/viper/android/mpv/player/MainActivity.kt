package io.viper.android.mpv.player

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val mainScreenFragment: Fragment by lazy {
        MainScreenFragment()
    }
    private val settingsFragment: Fragment by lazy {
        SettingsFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        switchFragment(NavigationPath.MAIN)
    }

    fun switchFragment(path: NavigationPath) {
        val targetFragment = when (path) {
            NavigationPath.MAIN -> mainScreenFragment
            NavigationPath.SETTINGS -> settingsFragment
        }
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_main_container_view)
        with(supportFragmentManager.beginTransaction()) {
            if (fragment == null) {
                add(R.id.fragment_main_container_view, targetFragment)
            } else if (targetFragment != fragment) {
                replace(R.id.fragment_main_container_view, targetFragment)
            }
            commit()
        }
    }
}