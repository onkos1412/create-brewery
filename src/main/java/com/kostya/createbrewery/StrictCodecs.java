package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Codec pieces that behave as on 1.21.1. The DataFixerUpper of 1.20.1 (6.0.8) has a lenient
 * {@code optionalFieldOf}: a field that is present but broken silently becomes the default. On 1.21.1 a broken
 * optional field is an error, and the mod relies on that (a bad datapack value must fail to load), so these
 * optional fields fall back only when the field is absent.
 */
public final class StrictCodecs {
    private StrictCodecs() {
    }

    /** An optional field: absent means {@code fallback}, present but invalid is an error; the default is not written. */
    public static <A> MapCodec<A> optionalField(Codec<A> codec, String name, A fallback) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(fallback) : codec.parse(ops, value);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return Objects.equals(input, fallback) ? prefix : prefix.add(name, codec.encodeStart(ops, input));
            }

            @Override
            public String toString() {
                return "StrictOptional[" + name + " -> " + codec + "]";
            }
        };
    }

    /** An optional field read as an {@link Optional}: absent is empty, present but invalid is an error. */
    public static <A> MapCodec<Optional<A>> optionalField(Codec<A> codec, String name) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(Optional.empty()) : codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, codec.encodeStart(ops, input.get())) : prefix;
            }

            @Override
            public String toString() {
                return "StrictOptional[" + name + " -> " + codec + "]";
            }
        };
    }

    /** 1.21.1's {@code Codec#validate}: decoding and encoding fail when {@code check} refuses the value. */
    public static <A> Codec<A> validate(Codec<A> codec, Function<A, DataResult<A>> check) {
        return codec.flatXmap(check, check);
    }

    /** 1.21.1's {@code MapCodec#validate}. */
    public static <A> MapCodec<A> validate(MapCodec<A> codec, Function<A, DataResult<A>> check) {
        return codec.flatXmap(check, check);
    }
}
