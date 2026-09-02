package org.hedgedoc.android

import android.app.Application
import org.hedgedoc.android.data.HedgeRepository

class HedgeApp : Application() {
    lateinit var repo: HedgeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = HedgeRepository(this)
    }
}
