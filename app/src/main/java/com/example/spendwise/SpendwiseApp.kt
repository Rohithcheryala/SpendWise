package com.example.spendwise

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.spendwise.core.parser_pw.bank.BankParserFactory
import com.example.spendwise.core.sms.SmsSyncWorker
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
class SpendwiseApp : Application(), Configuration.Provider {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Safety-net poll for bank SMS the live receiver missed (OEM power
        // management suppresses broadcasts on sleeping apps). KEEP policy:
        // rescheduling on every process start never restarts the clock.
        SmsSyncWorker.schedule(this)

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