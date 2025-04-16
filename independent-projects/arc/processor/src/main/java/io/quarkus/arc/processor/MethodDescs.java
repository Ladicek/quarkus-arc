package io.quarkus.arc.processor;

import jakarta.enterprise.context.spi.CreationalContext;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableReferenceProvider;
import io.quarkus.arc.impl.CreationalContextImpl;
import io.quarkus.gizmo2.desc.MethodDesc;

public final class MethodDescs {
    private MethodDescs() {
    }

    public static final MethodDesc CREATIONAL_CTX_CHILD = MethodDesc.of(CreationalContextImpl.class, "child",
            CreationalContextImpl.class, CreationalContext.class);

    public static final MethodDesc INJECTABLE_REF_PROVIDER_GET = MethodDesc.of(InjectableReferenceProvider.class, "get",
            Object.class, CreationalContext.class);

    public static final MethodDesc ARC_CONTAINER = MethodDesc.of(Arc.class, "container", ArcContainer.class);

    public static final MethodDesc ARC_CONTAINER_BEAN = MethodDesc.of(ArcContainer.class, "bean",
            InjectableBean.class, String.class);
}
