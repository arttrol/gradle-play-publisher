package com.github.triplet.gradle.play

import com.android.build.gradle.api.ApplicationVariant
import com.github.triplet.gradle.play.internal.JsonFileFilter
import com.github.triplet.gradle.play.internal.LISTINGS_PATH
import com.github.triplet.gradle.play.internal.LocaleFileFilter
import com.github.triplet.gradle.play.internal.PLAY_PATH
import com.github.triplet.gradle.play.internal.PRODUCTS_PATH
import com.github.triplet.gradle.play.internal.RELEASE_NOTES_PATH
import com.github.triplet.gradle.play.internal.climbUpTo
import com.github.triplet.gradle.play.internal.findClosestDir
import com.github.triplet.gradle.play.internal.isChildOf
import com.github.triplet.gradle.play.internal.isDirectChildOf
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File

@CacheableTask
open class GenerateResourcesTask : DefaultTask() {
    @get:Internal
    lateinit var variant: ApplicationVariant

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:OutputDirectory
    lateinit var resDir: File

    private val resSrcDirs: List<File> by lazy {
        variant.sourceSets.map { project.file("src/${it.name}/$PLAY_PATH") }
    }
    private val flavors by lazy { variant.baseName.split("-").run { take(size - 1) } }

    fun init() {
        for (dir in resSrcDirs) {
            inputs.dir(dir).skipWhenEmpty().withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }

    @TaskAction
    fun generate(inputs: IncrementalTaskInputs) {
        if (!inputs.isIncremental) project.delete(outputs.files)

        inputs.outOfDate {
            val file = it.file
            file.validate()

            project.copy { spec ->
                spec.from(file)
                spec.into(file.findClosestDir().findDest())
            }
        }
        inputs.removed { project.delete(it.file.findDest()) }
    }

    private fun File.validate() {
        fun File.validateLocales() {
            checkNotNull(listFiles()) {
                "$this must be a folder"
            }.forEach {
                check(it.isDirectory && LocaleFileFilter.accept(it)) {
                    "Invalid locale: ${it.name}"
                }
            }
        }

        fun validateListings() {
            val listings = climbUpTo(LISTINGS_PATH) ?: return
            check(listings.isDirectChildOf(PLAY_PATH)) {
                "Listings ($listings) must be under the '$PLAY_PATH' folder"
            }
            listings.validateLocales()
        }

        fun validateReleaseNotes() {
            val releaseNotes = climbUpTo(RELEASE_NOTES_PATH) ?: return
            check(releaseNotes.isDirectChildOf(PLAY_PATH)) {
                "Release notes ($releaseNotes) must be under the '$PLAY_PATH' folder"
            }
            releaseNotes.validateLocales()
        }

        fun validateProducts() {
            val products = climbUpTo(PRODUCTS_PATH) ?: return
            check(products.isDirectChildOf(PLAY_PATH)) {
                "Products ($products) must be under the '$PLAY_PATH' folder"
            }
            checkNotNull(products.listFiles()) {
                "$products must be a folder"
            }.forEach {
                check(JsonFileFilter.accept(it)) { "In-app product files must be JSON." }
            }
        }

        fun validateDuplicates() {
            if (isDirectory) return
            val flavor = flavors.singleOrNull { isChildOf(it) } ?: return
            val path = toRelativeString(climbUpTo(flavor)!!)
            flavors.filter { it != flavor }.forEach {
                check(!project.file("src/$it/$path").exists()) {
                    "File '$this' is duplicated in flavor $it with identical priority."
                }
            }
        }

        val areRootsValid = climbUpTo(LISTINGS_PATH) != null
                || climbUpTo(PRODUCTS_PATH) != null
                || climbUpTo(RELEASE_NOTES_PATH) != null
                || isDirectChildOf(PLAY_PATH)
        check(areRootsValid) { "Unknown file: $this" }

        validateListings()
        validateReleaseNotes()
        validateProducts()
        validateDuplicates()
    }

    private fun File.findDest() = File(resDir, toRelativeString(findOwner()))

    private fun File.findOwner() = resSrcDirs.single { startsWith(it) }
}
