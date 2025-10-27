package io.quarkus.arc.impl.invoke;

import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.invoke.AsyncHandler;

@AsyncHandler.ReturnType
public class CompletableFutureAsyncHandler<T> implements AsyncHandler<CompletableFuture<T>> {
    @Override
    public CompletableFuture<T> transform(CompletableFuture<T> original, Runnable completion) {
        return original.whenComplete((value, error) -> {
            completion.run();
        });
    }
}
