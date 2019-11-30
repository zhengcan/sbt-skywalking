package org.apache.skywalking.sbt

import java.io.{FilenameFilter, IOException, InputStream}
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream

import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.resolvedJavaAgents
import com.lightbend.sbt.javaagent.JavaAgent.ResolvedAgent
import com.lightbend.sbt.javaagent.{JavaAgent, Modules}
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.{Universal, dist, packageZipTarball, stage}
import sbt.Keys.{buildStructure, _}
import sbt.io.IO.{createDirectory, setModifiedTimeOrFalse, transfer}
import sbt.io.Using.{fileOutputStream, urlInputStream, zipInputStream}
import sbt.io.{AllPassFilter, NameFilter}
import sbt.{File, ModuleID, settingKey, task, taskKey, _}
import sbtassembly.AssemblyKeys._

import scala.annotation.tailrec
import scala.collection.mutable

case class ResolvedPlugin(plugin: ModuleID, artifact: File)

object SkyWalkingServiceKeys extends SkyWalkingKeys {
  val skyWalkingDirectory = settingKey[File](s"The directory of SkyWalking. (default ../tools/skywalking_${SkyWalkingDefaults.VERSION})")
  val skyWalkingMirror = settingKey[String](s"The mirror of apache download site. (default ${SkyWalkingDefaults.MIRROR})")
  val skyWalkingPlugins = settingKey[Seq[ModuleID]]("The custom skyWalking plugins")
  val skyWalkingPluginProjects = settingKey[Seq[ProjectReference]]("The custom skyWalking plugin projects")

  val skyWalkingResolvedPlugins = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking plugins")
  val skyWalkingAssemblyPluginProjects = taskKey[Unit]("Assembly custom skyWalking plugin projects")
  val skyWalkingResolvedPluginProjects = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking plugin projects")
  val skyWalkingDownload = taskKey[Seq[File]]("Download SkyWalking distribution if required.")
}

object SkyWalkingService extends AutoPlugin {
  override def requires = JavaAgent && UniversalPlugin

  val autoImport: SkyWalkingServiceKeys.type = SkyWalkingServiceKeys

  import SkyWalkingServiceKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    skyWalkingVersion := SkyWalkingDefaults.VERSION,
    skyWalkingDirectory := baseDirectory.value / s"../tools/skywalking-${SkyWalkingDefaults.VERSION}",
    skyWalkingMirror := SkyWalkingDefaults.MIRROR,
    skyWalkingPlugins := Seq.empty,
    skyWalkingPluginProjects := Seq.empty,
    skyWalkingResolvedPlugins := resolvePlugins.value,
    skyWalkingAssemblyPluginProjects := assemblyPluginProjects.value,
    skyWalkingResolvedPluginProjects := resolvePluginProjects.value,
    skyWalkingDownload := skyWalkingDownloadTask.value,

    libraryDependencies ++= Seq() ++
      // Plugins
      skyWalkingPlugins.value
        .map(plugin => plugin.withConfigurations(configurations = Option(Provided.name))),
    resolvedJavaAgents ++= resolveJavaAgents.value,
    mappings in Universal ++= mappingJavaAgents.value,
  ) ++ inConfig(Universal)(Seq(
    stage := (stage dependsOn ensureStage).value,
    dist := (dist dependsOn ensureStage).value,
    packageZipTarball := (packageZipTarball dependsOn ensureStage).value,
  ))

  def assemblyPluginProjects: Def.Initialize[Task[Unit]] = Def.taskDyn {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (assembly in p))
    }
    all
  }

  def ensureStage: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    if (enabled.value) {
      Def.task {
        skyWalkingDownload.value
        assemblyPluginProjects.value
      }
    }
    else Def.task {
      assemblyPluginProjects.value
    }
  }

  private def configDir: Def.Initialize[Task[File]] = Def.task[File] {
    baseDirectory.value / "./conf/skywalking"
  }

  private def enabled: Def.Initialize[Task[Boolean]] = Def.task[Boolean] {
    configDir.value.exists() && configDir.value.length() > 0
  }

  def resolveJavaAgents: Def.Initialize[Task[Seq[ResolvedAgent]]] = Def.task[Seq[ResolvedAgent]] {
    if (!enabled.value) {
      println("Skip adding SkyWalking as javaAgent due to no SkyWalking config")
      Seq.empty
    } else {
      Seq(
        ResolvedAgent(
          JavaAgent(SkyWalkingDefaults.GROUP_ID % SkyWalkingDefaults.ARTIFACT_ID % SkyWalkingDefaults.VERSION),
          skyWalkingDirectory.value / "agent/skywalking-agent.jar"
        )
      )
    }
  }

  def mappingJavaAgents: Def.Initialize[Task[Seq[(File, String)]]] = Def.taskDyn[Seq[(File, String)]] {
    if (!enabled.value) {
      Def.task {
        Seq.empty
      }
    } else {
      Def.task {
        // config
        configDir.value.listFiles()
          .map(file => Tuple2(file, s"${SkyWalkingDefaults.ARTIFACT_ID}/config/" + file.name)) ++
          // default activations
          agentJarFiles(skyWalkingDirectory.value, "activations", "activations") ++
          // default plugins
          agentJarFiles(skyWalkingDirectory.value, "plugins", "plugins") ++
          // optional plugins
          agentJarFiles(skyWalkingDirectory.value, "optional-plugins", "plugins") ++
          // custom plugins
          resolvePlugins.value
            .filter(plugin => plugin != null)
            .map(plugin => Tuple2(plugin.artifact, s"${SkyWalkingDefaults.ARTIFACT_ID}/plugins/" + plugin.artifact.name)) ++
          // custom plugin projects
          resolvePluginProjects.value
            .filter(plugin => plugin != null)
            .map(plugin => Tuple2(plugin.artifact, s"${SkyWalkingDefaults.ARTIFACT_ID}/plugins/" + plugin.artifact.name)) ++
          Seq()
      }
    }
  }

  def resolvePlugins: Def.Initialize[Task[Seq[ResolvedPlugin]]] = Def.task[Seq[ResolvedPlugin]] {
    skyWalkingPlugins.value flatMap { plugin =>
      (update in Provided).value.matching(Modules.exactFilter(plugin)).headOption map {
        jar => ResolvedPlugin(plugin, jar)
      }
    }
  }

  def resolvePluginProjects: Def.Initialize[Task[Seq[ResolvedPlugin]]] = Def.taskDyn[Seq[ResolvedPlugin]] {
    val stateTask = state.taskValue
    val structure = buildStructure.value
    val artTasks: Seq[Task[Seq[ResolvedPlugin]]] = skyWalkingPluginProjects.value flatMap { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .map(p => extractPlugins(stateTask, p))
    }

    val allPluginsTask: Task[Seq[ResolvedPlugin]] =
      artTasks.fold[Task[Seq[ResolvedPlugin]]](task(Nil)) { (previous, next) =>
        for {
          p <- previous
          n <- next
        } yield p ++ n
      }
    Def.task {
      allPluginsTask.value
    }
  }

  private def extractPlugins(stateTask: Task[State], ref: ProjectRef): Task[Seq[ResolvedPlugin]] =
    stateTask.flatMap { state =>
      val extracted: Extracted = Project.extract(state)
      val module: ModuleID = extracted.get(projectID in ref)
      val assemblyOutputPathTask = extracted.getOpt(assemblyOutputPath in ref in assembly).orNull
      for {
        assemblyOutputPath <- assemblyOutputPathTask
      } yield {
        Seq(
          ResolvedPlugin(module, assemblyOutputPath)
        )
      }
    }

  private val jarFileFilter = new FilenameFilter {
    override def accept(dir: File, name: String): Boolean = {
      name.endsWith(".jar")
    }
  }

  private def agentJarFiles(base: File, source: String, dest: String): Seq[(File, String)] = {
    val dir = base / "agent" / source
    if (dir.exists() && dir.isDirectory) {
      dir.listFiles(jarFileFilter)
        .map(file => Tuple2(file, s"${SkyWalkingDefaults.ARTIFACT_ID}/$dest/" + file.name))
    } else {
      Seq()
    }
  }

  def skyWalkingDownloadTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val dir = skyWalkingDirectory.value
    if (!dir.exists() || dir.length() == 0) {
      SkyWalkingDownloader.download(skyWalkingMirror.value, skyWalkingVersion.value, dir)
    }
    Nil
  }
}

object SkyWalkingDownloader {
  val lock = new ReentrantLock()

  def download(mirror: String, version: String, dest: File): Unit = {
    lock.lock()
    try {
      if (!dest.exists() || dest.length() == 0) {
        val link = s"${mirror}/skywalking/${version}/apache-skywalking-apm-${version}.zip"
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
