package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeleteCategory(
    private val categoryRepository: CategoryRepository,
    private val mangaRepository: MangaRepository,
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
) {

    suspend fun await(categoryId: Long) = withNonCancellableContext {
        /* Remove deleted category from library items */
        val libraryMangaList = mangaRepository.getLibraryManga()
        for (libraryManga in libraryMangaList) {
            if (libraryManga.category != categoryId) {
                continue
            }
            val manga = libraryManga.manga
            val categoryIds = getCategories.await(manga.id)
                .map { it.id }
                .subtract(setOf(categoryId))
                .toList()

            setMangaCategories.await(manga.id, categoryIds)
        }

        try {
            categoryRepository.delete(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = categoryRepository.getAll()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        try {
            categoryRepository.updatePartial(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
