package com.agon.app.utils

import timber.log.Timber

object AppLogger {
    fun d(message: String, vararg args: Any) = Timber.d(message, *args)
    fun i(message: String, vararg args: Any) = Timber.i(message, *args)
    fun w(message: String, vararg args: Any) = Timber.w(message, *args)
    fun w(t: Throwable, message: String, vararg args: Any) = Timber.w(t, message, *args)
    fun e(message: String, vararg args: Any) = Timber.e(message, *args)
    fun e(t: Throwable, message: String, vararg args: Any) = Timber.e(t, message, *args)
}
