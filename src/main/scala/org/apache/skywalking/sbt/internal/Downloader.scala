package org.apache.skywalking.sbt.internal

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import sbt.io.IO.{createDirectory, setModifiedTimeOrFalse, transfer}
import sbt.io.Using.{fileOutputStream, urlInputStream}
import sbt.io.{AllPassFilter, NameFilter}

import java.io.{File, IOException, InputStream}
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import scala.annotation.tailrec
import scala.collection.mutable

object Downloader {
  val lock = new ReentrantLock()

  def download(mirror: String, version: String, dest: File): Unit = {
    lock.lock()
    try {
      if (!dest.exists() || dest.length() == 0) {
        val link = s"$mirror/$version/apache-skywalking-apm-$version.tar.gz".replaceAllLiterally("//", "/")
        println(s"Download and unzip SkyWalking from $link to $dest...")
        if (link.endsWith(".tar.gz")) {
          untarURL(new URL(link), dest)
        } else if (link.endsWith(".zip")) {
          unzipURL(new URL(link), dest)
        } else {
          println(s"Unknown file format")
        }
      }
    } finally {
      lock.unlock()
    }
  }

  private def untarURL(
                        from: URL,
                        toDirectory: File,
                        filter: NameFilter = AllPassFilter,
                        preserveLastModified: Boolean = true
                      ): Set[File] =
    urlInputStream(from)(in => untarStream(in, toDirectory, filter, preserveLastModified))

  private def untarStream(
                           from: InputStream,
                           toDirectory: File,
                           filter: NameFilter = AllPassFilter,
                           preserveLastModified: Boolean = true
                         ): Set[File] = {
    createDirectory(toDirectory)
    val gzipInput = new GzipCompressorInputStream(from);
    val tarInput = new TarArchiveInputStream(gzipInput);
    extractWithoutTopDirectory(tarInput, toDirectory, filter, preserveLastModified)
  }

  private def unzipURL(
                        from: URL,
                        toDirectory: File,
                        filter: NameFilter = AllPassFilter,
                        preserveLastModified: Boolean = true
                      ): Set[File] =
    urlInputStream(from)(in => unzipStream(in, toDirectory, filter, preserveLastModified))

  private def unzipStream(
                           from: InputStream,
                           toDirectory: File,
                           filter: NameFilter = AllPassFilter,
                           preserveLastModified: Boolean = true
                         ): Set[File] = {
    createDirectory(toDirectory)
    val zipInput = new ZipArchiveInputStream(from)
    extractWithoutTopDirectory(zipInput, toDirectory, filter, preserveLastModified)
  }

  private def extractWithoutTopDirectory(
                          from: ArchiveInputStream,
                          toDirectory: File,
                          filter: NameFilter,
                          preserveLastModified: Boolean
                        ): Set[File] = {
    val set = new mutable.HashSet[File]

    @tailrec def next(): Unit = {
      val entry = from.getNextEntry
      if (entry == null)
        ()
      else {
        val name = entry.getName
        if (filter.accept(name)) {
          val topDirectoryIndex = name.indexOf('/')
          var target = new File(toDirectory, name)

          // remove top directory
          if (topDirectoryIndex > 0) {
            target = new File(toDirectory, name.substring(topDirectoryIndex))
          }

          //log.debug("Extracting zip entry '" + name + "' to '" + target + "'")
          if (entry.isDirectory)
            createDirectory(target)
          else {
            set += target
            translate("Error extracting zip entry '" + name + "' to '" + target + "': ") {
              fileOutputStream(false)(target)(out => transfer(from, out))
            }
          }
          if (preserveLastModified)
            setModifiedTimeOrFalse(target, entry.getLastModifiedDate.getTime)
        } else {
          //log.debug("Ignoring zip entry '" + name + "'")
        }
//        from.closeEntry()
        next()
      }
    }

    next()
    Set() ++ set
  }

  private def translate[T](msg: => String)(f: => T) =
    try {
      f
    } catch {
      case e: IOException => throw new IOException(msg + e.toString, e)
      case e: Exception => throw new Exception(msg + e.toString, e)
    }
}
