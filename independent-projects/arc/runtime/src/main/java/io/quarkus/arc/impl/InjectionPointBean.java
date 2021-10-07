package io.quarkus.arc.impl;

import java.lang.reflect.Type;
import java.util.Set;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;

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
