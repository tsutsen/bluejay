package com.tsutsen.platformplayer.core.data.repository.impl;

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
public final class LibraryRepositoryImpl_Factory implements Factory<LibraryRepositoryImpl> {
  @Override
  public LibraryRepositoryImpl get() {
    return newInstance();
  }

  public static LibraryRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LibraryRepositoryImpl newInstance() {
    return new LibraryRepositoryImpl();
  }

  private static final class InstanceHolder {
    static final LibraryRepositoryImpl_Factory INSTANCE = new LibraryRepositoryImpl_Factory();
  }
}
