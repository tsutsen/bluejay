package com.tsutsen.platformplayer.api.media.models.video

import com.tsutsen.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.tsutsen.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.tsutsen.platformplayer.api.media.models.streams.sources.*
import com.tsutsen.platformplayer.serializers.VideoDescriptorSerializer

@kotlinx.serialization.Serializable(with = VideoDescriptorSerializer::class)
interface ISerializedVideoSourceDescriptor: IVideoSourceDescriptor {

    companion object {
        fun fromDescriptor(descriptor: IVideoSourceDescriptor): ISerializedVideoSourceDescriptor {
            val videoSources = descriptor.videoSources
                .filter { it is IVideoUrlSource }
                .map { VideoUrlSource.fromUrlSource(it as IVideoUrlSource)!! }
                .toTypedArray();

            if(descriptor !is VideoUnMuxedSourceDescriptor)
                return SerializedVideoMuxedSourceDescriptor(videoSources);
            else
                return SerializedVideoNonMuxedSourceDescriptor(videoSources, descriptor.audioSources
                    .filter { it is IAudioUrlSource }
                    .map { AudioUrlSource.fromUrlSource(it as IAudioUrlSource)!! }
                    .toTypedArray());
        }
    }
};