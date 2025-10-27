package io.quarkus.arc.impl.invoke;

import jakarta.enterprise.invoke.AsyncHandler;

import io.smallrye.mutiny.Uni;

@AsyncHandler.ReturnType
public class UniAsyncHandler<T> implements AsyncHandler<Uni<T>> {
    @Override
    public Uni<T> transform(Uni<T> original, Runnable completion) {
        return original.onTermination().invoke(completion);
    }
}
