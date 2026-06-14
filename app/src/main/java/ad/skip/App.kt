package ad.skip

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        G.init(this)
    }
}
