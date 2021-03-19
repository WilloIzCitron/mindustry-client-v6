package com.github.blahblahbloopster

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class CustomGetterSetter(val getterName: String, val setterName: String)
