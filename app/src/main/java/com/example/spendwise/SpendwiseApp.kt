package com.example.spendwise

import android.app.Application
import com.example.spendwise.core.parser_pw.bank.BankParserFactory
import com.example.spendwise.data.database.DatabaseSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn

import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class SpendwiseApp : Application() {
    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            databaseSeeder.seed()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    @Provides
    @Singleton
    fun provideBankParserFactory(): BankParserFactory {
        return BankParserFactory
    }
}