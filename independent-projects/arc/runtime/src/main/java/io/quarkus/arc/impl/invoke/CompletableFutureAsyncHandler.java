package io.quarkus.arc.impl.invoke;

import jakarta.enterprise.invoke.AsyncHandler;

@AsyncHandler.ReturnType
public class CompletableFutureAsyncHandler<T> implements AsyncHandler<java.util.concurrent.CompletableFuture<T>> {
    @Override
    public java.util.concurrent.CompletableFuture<T> transform(java.util.concurrent.CompletableFuture<T> original,
            Runnable completion) {
        return original.whenComplete((value, error) -> {
            completion.run();
        });
    }
}
