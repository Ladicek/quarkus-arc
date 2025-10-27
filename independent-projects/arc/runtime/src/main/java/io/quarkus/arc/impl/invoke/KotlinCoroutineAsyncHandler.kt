package io.quarkus.arc.impl.invoke

import jakarta.enterprise.invoke.AsyncHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

@AsyncHandler.ParameterType
class KotlinCoroutineAsyncHandler<T> : AsyncHandler<Continuation<T>> {
    override fun transform(original: Continuation<T>, completion: Runnable): Continuation<T> {
        return object : Continuation<T> {
            override fun resumeWith(result: Result<T>) {
                completion.run()
                original.resumeWith(result)
            }

            override val context: CoroutineContext
                get() = original.context
        }
    }
}
