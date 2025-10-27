package io.quarkus.arc.impl.invoke;

import jakarta.enterprise.invoke.AsyncHandler;

import io.smallrye.mutiny.Multi;

@AsyncHandler.ReturnType
public class MultiAsyncHandler<T> implements AsyncHandler<Multi<T>> {
    @Override
    public Multi<T> transform(Multi<T> original, Runnable completion) {
        return original.onTermination().invoke(completion);
    }
}
