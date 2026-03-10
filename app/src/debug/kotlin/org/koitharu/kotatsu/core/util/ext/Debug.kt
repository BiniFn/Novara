package org.skepsun.kototoro.core.util.ext

import android.os.Looper


fun assertNotInMainThread() = check(Looper.myLooper() != Looper.getMainLooper()) {
	"Calling this from the main thread is prohibited"
}
