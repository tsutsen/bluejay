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
public final class SettingsRepositoryImpl_Factory implements Factory<SettingsRepositoryImpl> {
  private final Provider<Context> contextProvider;

  private SettingsRepositoryImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsRepositoryImpl get() {
    return newInstance(contextProvider.get());
  }

  public static SettingsRepositoryImpl_Factory create(Provider<Context> contextProvider) {
    return new SettingsRepositoryImpl_Factory(contextProvider);
  }

  public static SettingsRepositoryImpl newInstance(Context context) {
    return new SettingsRepositoryImpl(context);
  }
}
