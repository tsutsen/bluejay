package com.futo.platformplayer.core.data.repository.impl;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class HomeRepositoryImpl_Factory implements Factory<HomeRepositoryImpl> {
  @Override
  public HomeRepositoryImpl get() {
    return newInstance();
  }

  public static HomeRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static HomeRepositoryImpl newInstance() {
    return new HomeRepositoryImpl();
  }

  private static final class InstanceHolder {
    static final HomeRepositoryImpl_Factory INSTANCE = new HomeRepositoryImpl_Factory();
  }
}
