package parity

import com.pathways.rules.content.ContentLoader
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

/**
 * Every public no-argument accessor of [ContentLoader], decoded: the TS
 * loader must apply the same defaults, codes and sorting to the same files.
 */
fun Vectors.contentVectors() {
    val accessors = ContentLoader::class.declaredMemberFunctions
        .filter { it.visibility == KVisibility.PUBLIC && it.parameters.size == 1 }
        .sortedBy { it.name }
    for (f in accessors) case("ContentLoader.${f.name}") { f.call(ContentLoader) }
}
