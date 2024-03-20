package io.viper.android.mpv

interface IPlayer {
    fun init(configDir: String, cacheDir: String)
    fun playFile(fp: String)
}