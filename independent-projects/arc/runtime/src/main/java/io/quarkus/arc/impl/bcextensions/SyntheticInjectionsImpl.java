package io.quarkus.arc.impl.bcextensions;

import java.lang.annotation.Annotation;
import java.util.Map;

import jakarta.enterprise.inject.build.compatible.spi.SyntheticInjections;
import jakarta.enterprise.util.TypeLiteral;

import io.quarkus.arc.impl.SyntheticCreationalContextImpl;

public class SyntheticInjectionsImpl implements SyntheticInjections {
    private final Map<SyntheticCreationalContextImpl.TypeAndQualifiers, Object> injections;

    public SyntheticInjectionsImpl(Map<SyntheticCreationalContextImpl.TypeAndQualifiers, Object> injections) {
        this.injections = injections;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type, Annotation... qualifiers) {
        if (qualifiers.length == 0) {
            // `null` gets translated to `@Default` by `TypeAndQualifiers` ctor
            qualifiers = null;
        }
        return (T) injections.get(new SyntheticCreationalContextImpl.TypeAndQualifiers(type, qualifiers));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(TypeLiteral<T> type, Annotation... qualifiers) {
        if (qualifiers.length == 0) {
            // `null` gets translated to `@Default` by `TypeAndQualifiers` ctor
            qualifiers = null;
        }
        return (T) injections.get(new SyntheticCreationalContextImpl.TypeAndQualifiers(type.getType(), qualifiers));
    }
}
