package com.tsutsen.platformplayer.api.media.exceptions

class NoPlatformClientException(s: String) : IllegalArgumentException("No enabled PlatformClient: $s") {}