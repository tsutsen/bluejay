package com.tsutsen.platformplayer.api.media.models.ratings

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.orDefault
import com.tsutsen.platformplayer.serializers.IRatingSerializer

@kotlinx.serialization.Serializable(with = IRatingSerializer::class)
interface IRating {
    val type : RatingType;


    companion object {
        fun fromV8OrDefault(config: IV8PluginConfig, obj: V8Value?, default: IRating): IRating {
            obj?.ensureIsBusy();
            return obj.orDefault(default) { fromV8(config, it as V8ValueObject) }
        };
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject, contextName: String = "Rating") : IRating {
            obj.ensureIsBusy();
            val t = RatingType.fromInt(obj.getOrThrow<Int>(config, "type", contextName));
            return when(t) {
                RatingType.LIKES -> RatingLikes.fromV8(config, obj);
                RatingType.LIKEDISLIKES -> RatingLikeDislikes.fromV8(config, obj);
                RatingType.SCALE -> RatingScaler.fromV8(config, obj);
                else -> throw NotImplementedError("Unknown type $t");
            }
        }
    }
}