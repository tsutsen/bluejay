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
public final class SearchRepositoryImpl_Factory implements Factory<SearchRepositoryImpl> {
  @Override
  public SearchRepositoryImpl get() {
    return newInstance();
  }

  public static SearchRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SearchRepositoryImpl newInstance() {
    return new SearchRepositoryImpl();
  }

  private static final class InstanceHolder {
    static final SearchRepositoryImpl_Factory INSTANCE = new SearchRepositoryImpl_Factory();
  }
}
