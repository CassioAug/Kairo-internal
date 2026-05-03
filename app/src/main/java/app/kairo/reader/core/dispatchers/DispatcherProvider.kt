package app.kairo.reader.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

@Suppress("InjectDispatcher")
class DefaultDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
