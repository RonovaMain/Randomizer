package dev.ronova.randomizer

import android.app.Application
import android.content.Context
import dev.ronova.randomizer.data.AppDb
import dev.ronova.randomizer.data.PhotoStore
import dev.ronova.randomizer.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RandomizerApp : Application() {
    lateinit var db: AppDb
    lateinit var photos: PhotoStore
    lateinit var repo: Repo
    lateinit var feedback: Feedback

    override fun onCreate() {
        super.onCreate()
        db = AppDb.create(this)
        photos = PhotoStore(this)
        repo = Repo(db, photos)
        feedback = Feedback(this)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        CoroutineScope(Dispatchers.IO).launch {
            repo.seedIfNeeded(prefs)
            repo.gcPhotos()
        }
    }
}
