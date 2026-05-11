package com.lagradost.api

import java.lang.ref.WeakReference

private var appContextRef: WeakReference<Any>? = null

fun setContext(context: WeakReference<Any>) {
	appContextRef = context
}

fun getContext(): Any? = appContextRef?.get()
