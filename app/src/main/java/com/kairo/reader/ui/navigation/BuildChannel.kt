package com.kairo.reader.ui.navigation

import android.content.pm.ApplicationInfo
import com.kairo.reader.KairoApplication

internal fun KairoApplication.isDebuggableBuild(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
