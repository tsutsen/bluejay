package com.tsutsen.platformplayer.views.fields

/**
 * Annotation for marking a Settings field as a button.
 * Replaces the deleted ButtonField.kt which contained this annotation.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormFieldButton(val drawable: String = "")
