package org.apache.skywalking.sbt.internal

import java.io.{File, IOException, InputStream}
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream

import sbt.io.IO.{createDirectory, setModifiedTimeOrFalse, transfer}
import sbt.io.{AllPassFilter, NameFilter}
import sbt.io.Using.{fileOutputStream, urlInputStream, zipInputStream}

import scala.annotation.tailrec
import scala.collection.mutable

object Downloader {
  val lock = new ReentrantLock()

  def download(mirror: String, version: String, dest: File): Unit = {
    lock.lock()
    try {
      if (!dest.exists() || dest.length() == 0) {
        val link = s"$mirror/skywalking/$version/apache-skywalking-apm-$version.zip"
        println(s"Download and unzip SkyWalking from $link to $dest...")
        unzipURL(new URL(link), dest)
      }
    } finally {
      lock.unlock()
    }
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
    zipInputStream(from)(zipInput => extractWithoutTopDirectory(zipInput, toDirectory, filter, preserveLastModified))
  }

  private def extractWithoutTopDirectory(
                                          from: ZipInputStream,
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
            setModifiedTimeOrFalse(target, entry.getTime)
        } else {
          //log.debug("Ignoring zip entry '" + name + "'")
        }
        from.closeEntry()
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
