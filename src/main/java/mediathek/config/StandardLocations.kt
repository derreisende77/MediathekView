/*
 * Copyright (c) 2025 derreisende77.
 * This code was developed as part of the MediathekView project https://github.com/mediathekview/MediathekView
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package mediathek.config

import mediathek.filmlisten.FilmListDownloadType
import org.apache.commons.lang3.SystemUtils
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object StandardLocations {
    /**
     * The base directory when app is run in portable mode.
     */
    var portableBaseDirectory: String? = null
    private val logger = LogManager.getLogger()

    /**
     * Return the location of the settings directory.
     * If it does not exist, create one.
     *
     * @return Path to the settings directory
     * @throws IllegalStateException Will be thrown if settings directory doesn't exist and if there is an error on creating it.
     */
    @JvmStatic
    @Throws(IllegalStateException::class)
    fun getSettingsDirectory(): Path {
        val baseDirectoryPath: Path = if (portableBaseDirectory == null || portableBaseDirectory!!.isEmpty()) {
            Paths.get(SystemUtils.USER_HOME, Konstanten.VERZEICHNIS_EINSTELLUNGEN)
        } else {
            Paths.get(portableBaseDirectory!!)
        }
        if (Files.notExists(baseDirectoryPath)) {
            try {
                Files.createDirectories(baseDirectoryPath)
            } catch (ioException: IOException) {
                val errMsg = String.format(
                    "Der Ordner \"%s\" konnte nicht angelegt werden.%n Bitte prüfen Sie die Dateirechte.",
                    baseDirectoryPath.toString()
                )
                logger.error(errMsg, ioException)
                throw IllegalStateException(errMsg, ioException)
            }
        }
        return baseDirectoryPath
    }

    /**
     * Return the path to "bookmarks.json"
     *
     * @return Path object of bookmark file
     */
    @JvmStatic
    fun getBookmarkFilePath(): Path {
        return getSettingsDirectory().resolve(Konstanten.BOOKMARK_FILE)
    }

    /**
     * Return the path to "mediathek.xml"
     *
     * @return Path to the file
     */
    @JvmStatic
    @Throws(InvalidPathException::class)
    fun getMediathekXmlFile(): Path {
        return getSettingsDirectory().resolve(Konstanten.CONFIG_FILE)
    }

    @JvmStatic
    @Throws(InvalidPathException::class)
    fun getXDGDownloadDirectory(): Optional<Path> {
        return try {
            val process = ProcessBuilder("xdg-user-dir", "DOWNLOAD")
                .directory(File(SystemUtils.USER_HOME))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            val line = process.inputReader().use { reader -> reader.readLine() }
            Optional.of(line).filter { s -> s.isNotEmpty() }.map { s -> Paths.get(s) }
        } catch (_: IOException) {
            Optional.empty()
        }
    }

    /**
     * Return the standard path to downloads.
     *
     * @return Standard path to the download directory.
     */
    @JvmStatic
    @Throws(InvalidPathException::class)
    fun getStandardDownloadPath(): String {
        val userHome = SystemUtils.USER_HOME
        val path = if (SystemUtils.IS_OS_MAC_OSX)
            Paths.get(userHome, "Downloads")
        else if (SystemUtils.IS_OS_LINUX)
            getXDGDownloadDirectory().orElse(Paths.get(userHome, Konstanten.VERZEICHNIS_DOWNLOADS))
        else
            Paths.get(userHome, Konstanten.VERZEICHNIS_DOWNLOADS)
        return path.toAbsolutePath().toString()
    }

    /**
     * Get the address of the used film list type as string.
     *
     * @param type which list to use.
     * @return URL of filmlist as String.
     */
    @JvmStatic
    fun getFilmListUrl(type: FilmListDownloadType): String {
        return when (type) {
            FilmListDownloadType.FULL -> Konstanten.ROUTER_BASE_URL.resolve(Konstanten.FULL_FILM_LIST).toString()
            FilmListDownloadType.DIFF_ONLY -> Konstanten.ROUTER_BASE_URL.resolve(Konstanten.DIFF_FILM_LIST).toString()
        }
    }

    private const val OSX_CACHE_DIRECTORY_NAME = "Library/Caches/MediathekView"
    private const val LOCKFILE_NAME = "MediathekView.lock"

    /**
     * Return the string path to the filmlist.
     *
     * @return the path as String.
     */
    @JvmStatic
    fun getFilmlistFilePathString(): String {
        val filePart = File.separator + Konstanten.JSON_DATEI_FILME
        return if (Config.isPortableMode())
            getSettingsDirectory().toString() + filePart
        else {
            if (SystemUtils.IS_OS_MAC_OSX) {
                //place filmlist into OS X user cache directory in order not to backup it all the time in TimeMachine...
                SystemUtils.USER_HOME + File.separator + OSX_CACHE_DIRECTORY_NAME + filePart
            } else {
                getSettingsDirectory().toString() + filePart
            }
        }
    }

    /**
     * Return the location of the lucene film index.
     */
    @JvmStatic
    fun getFilmIndexPath(): Path {
        val indexDirectory = "mv_index"

        return if (Config.isPortableMode())
            getSettingsDirectory().resolve(indexDirectory)
        else {
            if (SystemUtils.IS_OS_MAC_OSX) {
                val base = Paths.get(SystemUtils.USER_HOME + File.separator + OSX_CACHE_DIRECTORY_NAME)
                base.resolve(indexDirectory)
            } else {
                getSettingsDirectory().resolve(indexDirectory)
            }
        }
    }
    /**
     * Return the path to the lockfile.
     * On macOS we do not support roaming settings with the official app, therefore keep the old temp dir convention.
     * On linux and windows we do support now multiple instances running with different "portable" settings directories.
     * Therefore store the lock file now in the settings directory during runtime.
     *
     * @return The Path object to the lockfile
     */
    @JvmStatic
    fun getLockFilePath(): Path {
        return getSettingsDirectory().resolve(LOCKFILE_NAME)
    }
}