package io.quarkus.arc.impl.invoke;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.invoke.AsyncHandler;

@AsyncHandler.ReturnType
public class CompletionStageAsyncHandler<T> implements AsyncHandler<CompletionStage<T>> {
    @Override
    public CompletionStage<T> transform(CompletionStage<T> original, Runnable completion) {
        return original.whenComplete((value, error) -> {
            completion.run();
        });
    }
}
