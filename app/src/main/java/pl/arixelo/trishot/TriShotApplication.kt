package pl.arixelo.trishot

import android.app.Application
import pl.arixelo.trishot.capture.GestureMode
import pl.arixelo.trishot.data.ShotRepository

class TriShotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShotRepository.init(this)
        GestureMode.init(this)
    }
}
