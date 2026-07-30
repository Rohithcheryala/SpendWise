package com.example.spendwise.core.di

import com.example.spendwise.core.contacts.AndroidContactProvider
import com.example.spendwise.core.contacts.ContactProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactModule {

    @Binds
    @Singleton
    abstract fun bindContactProvider(
        impl: AndroidContactProvider
    ): ContactProvider
}