package com.tsutsen.platformplayer.encryption

class GPasswordEncryptionProvider {
    companion object {
        val version = 1;
        val instance = GPasswordEncryptionProviderV1.instance;
    }
}