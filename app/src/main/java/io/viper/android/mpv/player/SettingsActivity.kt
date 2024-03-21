package io.viper.android.mpv.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity(R.layout.activity_frag_container),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(supportFragmentManager.beginTransaction()) {
            add(R.id.fragment_container_view, SettingsFragment())
            commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                // 当页面内部返回时，恢复settings的标题
                setTitle(R.string.title_activity_settings)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            // 防止在子fragment中点击返回键直接返回指定的父activity
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // 拦截子fragment打开，并设置当前title
        setTitle(pref.title)
        return false
    }
}