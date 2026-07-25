package com.tsutsen.platformplayer.feature.dualscreen;

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
public final class CompanionWindowManager_Factory implements Factory<CompanionWindowManager> {
  private final Provider<Context> contextProvider;

  private CompanionWindowManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CompanionWindowManager get() {
    return newInstance(contextProvider.get());
  }

  public static CompanionWindowManager_Factory create(Provider<Context> contextProvider) {
    return new CompanionWindowManager_Factory(contextProvider);
  }

  public static CompanionWindowManager newInstance(Context context) {
    return new CompanionWindowManager(context);
  }
}
