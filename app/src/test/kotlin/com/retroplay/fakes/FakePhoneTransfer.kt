package com.retroplay.fakes

import com.retroplay.data.di.PhoneTransferModule
import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.PhoneTransfer
import com.retroplay.domain.transfer.PhoneTransferState
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

/** Transferência falsa: registra quantas telas estão usando o servidor. */
class FakePhoneTransfer : PhoneTransfer {
    var users = 0
    var running = PhoneTransferState.Running("http://192.168.0.15:8080", emptyList(), "4821", "http://192.168.0.15:8080/?pin=4821")
    override val state = MutableStateFlow<PhoneTransferState>(PhoneTransferState.Stopped)
    override val incoming = MutableStateFlow<List<IncomingFile>>(emptyList())

    override fun acquire() {
        users++
        state.value = running
    }

    override fun release() {
        users--
        if (users == 0) state.value = PhoneTransferState.Stopped
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PhoneTransferModule::class])
object FakePhoneTransferModule {
    @Provides
    @Singleton
    fun fake(): FakePhoneTransfer = FakePhoneTransfer()

    @Provides
    @Singleton
    fun phone(fake: FakePhoneTransfer): PhoneTransfer = fake
}
