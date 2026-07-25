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
public final class SubscriptionRepositoryImpl_Factory implements Factory<SubscriptionRepositoryImpl> {
  @Override
  public SubscriptionRepositoryImpl get() {
    return newInstance();
  }

  public static SubscriptionRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SubscriptionRepositoryImpl newInstance() {
    return new SubscriptionRepositoryImpl();
  }

  private static final class InstanceHolder {
    static final SubscriptionRepositoryImpl_Factory INSTANCE = new SubscriptionRepositoryImpl_Factory();
  }
}
