package com.tsutsen.platformplayer.core.data.repository.impl;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PlayerRepositoryImpl_Factory implements Factory<PlayerRepositoryImpl> {
  private final Provider<Context> contextProvider;

  private PlayerRepositoryImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PlayerRepositoryImpl get() {
    return newInstance(contextProvider.get());
  }

  public static PlayerRepositoryImpl_Factory create(Provider<Context> contextProvider) {
    return new PlayerRepositoryImpl_Factory(contextProvider);
  }

  public static PlayerRepositoryImpl newInstance(Context context) {
    return new PlayerRepositoryImpl(context);
  }
}
