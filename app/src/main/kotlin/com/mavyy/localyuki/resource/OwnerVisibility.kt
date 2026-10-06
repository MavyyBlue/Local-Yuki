package com.mavyy.localyuki.resource
/** Actual Android activity lifecycle, never model-controlled foreground claims. */
object OwnerVisibility { @Volatile var active=false;internal set }
