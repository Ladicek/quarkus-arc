package io.quarkus.arc.impl.invoke;

import java.util.concurrent.Flow;

import jakarta.enterprise.invoke.AsyncHandler;

// TODO run the RS TCK with this
@AsyncHandler.ReturnType
public class FlowPublisherAsyncHandler<T> implements AsyncHandler<Flow.Publisher<T>> {
    @Override
    public Flow.Publisher<T> transform(Flow.Publisher<T> original, Runnable completion) {
        return new Flow.Publisher<T>() {
            @Override
            public void subscribe(Flow.Subscriber<? super T> subscriber) {
                original.subscribe(new Flow.Subscriber<T>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(T item) {
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        completion.run();
                        subscriber.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        completion.run();
                        subscriber.onComplete();
                    }
                });
            }
        };
    }
}
