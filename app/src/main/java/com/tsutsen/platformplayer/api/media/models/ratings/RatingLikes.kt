package com.tsutsen.platformplayer.api.media.models.ratings

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrThrow

/**
 * A rating that has just likes
 */
@kotlinx.serialization.Serializable
class RatingLikes(val likes: Long) : IRating {
    override val type: RatingType = RatingType.LIKES;

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : RatingLikes {
            obj.ensureIsBusy();
            return RatingLikes(obj.getOrThrow(config, "likes", "RatingLikes"));
        }
    }
}