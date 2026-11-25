package com.example

import android.content.Context
import com.example.data.MediaRepository
import com.example.data.StatsRepository

interface AppContainer {
    val mediaRepository: MediaRepository
    val statsRepository: StatsRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val mediaRepository: MediaRepository by lazy {
        MediaRepository(context)
    }

    override val statsRepository: StatsRepository by lazy {
        StatsRepository(context)
    }
}
