@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.navigation.NavGraphBuilder

internal data class KairoNavGraphDependencies(
    val library: LibraryDestinationDependencies,
    val reader: ReaderDestinationDependencies,
    val rsvp: RsvpDestinationDependencies,
    val settings: SettingsRouteDependencies,
)

internal fun NavGraphBuilder.kairoNavGraph(dependencies: KairoNavGraphDependencies) {
    libraryDestinations(dependencies.library)
    readerDestinations(dependencies.reader)
    rsvpDestination(dependencies.rsvp)
    bionicDestination(dependencies.rsvp)
    settingsRoutes(dependencies.settings)
}
