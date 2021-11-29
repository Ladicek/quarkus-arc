package io.quarkus.arc.impl;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.lang.reflect.Type;
import java.util.Set;

public class InjectionPointBean extends BuiltInBean<InjectionPoint> {
    public static final Set<Type> EVENT_TYPES = Set.of(InjectionPoint.class, Object.class);

    @Override
    public Set<Type> getTypes() {
        return EVENT_TYPES;
    }

    @Override
    public InjectionPoint get(CreationalContext<InjectionPoint> creationalContext) {
        return InjectionPointProvider.get();
    }

    @Override
    public Class<?> getBeanClass() {
        return CurrentInjectionPointProvider.InjectionPointImpl.class;
    }
}
