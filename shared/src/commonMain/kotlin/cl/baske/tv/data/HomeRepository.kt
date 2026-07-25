package cl.baske.tv.data

import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.model.ItemsResponse
import cl.baske.tv.data.remote.EmbyApi
import kotlinx.coroutines.flow.first
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

/**
 * Fuente de datos del Home. Cada sección es un Store5 cacheado en memoria y
 * deduplicado; `fresh = true` fuerza red (para el refresh manual y, más
 * adelante, para revalidar Continuar Viendo al volver del player).
 *
 * Aún sin SourceOfTruth (SQLDelight) — se puede sumar después sin tocar a los
 * consumidores.
 */
class HomeRepository(private val api: EmbyApi) {

    private val viewsStore: Store<String, ItemsResponse> =
        StoreBuilder.from(Fetcher.of<String, ItemsResponse> { api.getViews(it) }).build()

    private val resumeStore: Store<String, ItemsResponse> =
        StoreBuilder.from(Fetcher.of<String, ItemsResponse> { api.getResume(it) }).build()

    private val nextUpStore: Store<String, ItemsResponse> =
        StoreBuilder.from(Fetcher.of<String, ItemsResponse> { api.getNextUp(it) }).build()

    // Key = "userId|parentId" para cachear el latest de cada biblioteca por separado.
    private val latestStore: Store<String, List<BaseItemDto>> =
        StoreBuilder.from(Fetcher.of<String, List<BaseItemDto>> { key ->
            val (userId, parentId) = key.split('|', limit = 2)
            api.getLatestForLibrary(userId, parentId)
        }).build()

    suspend fun views(userId: String, fresh: Boolean = false): List<BaseItemDto> =
        viewsStore.once(userId, fresh).items

    suspend fun resume(userId: String, fresh: Boolean = false): List<BaseItemDto> =
        resumeStore.once(userId, fresh).items

    suspend fun nextUp(userId: String, fresh: Boolean = false): List<BaseItemDto> =
        nextUpStore.once(userId, fresh).items

    suspend fun latestForLibrary(userId: String, parentId: String, fresh: Boolean = false): List<BaseItemDto> =
        latestStore.once("$userId|$parentId", fresh)

    /**
     * Toma un único valor del Store: cacheado si existe (o red si no), o red
     * forzada con `fresh`. Propaga el error en vez de colgarse esperando Data.
     */
    private suspend fun <K : Any, V : Any> Store<K, V>.once(key: K, fresh: Boolean): V {
        val request =
            if (fresh) StoreReadRequest.fresh(key)
            else StoreReadRequest.cached(key, refresh = false)
        val response = stream(request).first {
            it is StoreReadResponse.Data<*> || it is StoreReadResponse.Error
        }
        return when (response) {
            is StoreReadResponse.Data -> response.value
            is StoreReadResponse.Error.Exception -> throw response.error
            is StoreReadResponse.Error.Message -> error(response.message)
            else -> error("Respuesta inesperada del Store")
        }
    }
}
