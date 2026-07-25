package com.tsutsen.platformplayer.stores.db

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ColumnOrdered(val priority: Int, val descending: Boolean = false);