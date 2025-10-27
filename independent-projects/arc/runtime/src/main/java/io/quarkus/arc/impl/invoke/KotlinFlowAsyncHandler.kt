package io.quarkus.arc.impl.invoke

import jakarta.enterprise.invoke.AsyncHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion

@AsyncHandler.ReturnType
class KotlinFlowAsyncHandler<T> : AsyncHandler<Flow<T>> {
    override fun transform(original: Flow<T>, completion: Runnable): Flow<T> {
        return original.onCompletion {
            completion.run()
        }
    }
}
