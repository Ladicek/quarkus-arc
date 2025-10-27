package io.quarkus.arc.impl.invoke;

import jakarta.enterprise.invoke.AsyncHandler;

@AsyncHandler.ReturnType
public class CompletionStageAsyncHandler<T> implements AsyncHandler<java.util.concurrent.CompletionStage<T>> {
    @Override
    public java.util.concurrent.CompletionStage<T> transform(java.util.concurrent.CompletionStage<T> original,
            Runnable completion) {
        return original.whenComplete((value, error) -> {
            completion.run();
        });
    }
}
