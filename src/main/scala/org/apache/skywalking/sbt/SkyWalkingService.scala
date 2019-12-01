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
import sbt.{Def, File, ModuleID, settingKey, task, taskKey, _}
import sbtassembly.AssemblyKeys._

import scala.annotation.tailrec
import scala.collection.mutable

case class ResolvedPlugin(plugin: ModuleID, artifact: File)

object SkyWalkingServiceKeys extends SkyWalkingKeys {
  val skyWalkingDirectory = settingKey[File](s"The directory of SkyWalking.")
  val skyWalkingMirror = settingKey[String](s"The mirror of apache download site. (default ${SkyWalkingDefaults.MIRROR})")
  val skyWalkingEnableDefaultActivations = settingKey[Boolean]("Enable default activations. (default: true)")
  val skyWalkingEnableDefaultPlugins = settingKey[Boolean]("Enable default plugins. (default: true)")
  val skyWalkingEnableOptionalPlugins = settingKey[Boolean]("Enable optional plugins. (default: false)")
  val skyWalkingEnableBootstrapPlugins = settingKey[Boolean]("Enable bootstrap plugins. (default: false)")
  val skyWalkingActivations = settingKey[Seq[ModuleID]]("The custom skyWalking activations")
  val skyWalkingPlugins = settingKey[Seq[ModuleID]]("The custom skyWalking plugins")
  val skyWalkingPluginProjects = settingKey[Seq[ProjectReference]]("The custom skyWalking plugin projects")
  val skyWalkingConfigDirectory = settingKey[File]("The agent config directory")

  val skyWalkingDefaultDirectory = taskKey[File](s"The default directory of SkyWalking. (default RootProject/tools/skywalking-${SkyWalkingDefaults.VERSION})")
  val skyWalkingResolvedActivations = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking activations")
  val skyWalkingResolvedPlugins = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking plugins")
  val skyWalkingAssemblyPluginProjects = taskKey[Unit]("Assembly custom skyWalking plugin projects")
  val skyWalkingResolvedPluginProjects = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking plugin projects")
  val skyWalkingDownloadDistribution = taskKey[Seq[File]]("Download SkyWalking distribution if required.")
}

object SkyWalkingService extends AutoPlugin {
  override def requires: Plugins = JavaAgent && UniversalPlugin

  val autoImport: SkyWalkingServiceKeys.type = SkyWalkingServiceKeys

  import SkyWalkingServiceKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    skyWalkingVersion := SkyWalkingDefaults.VERSION,
    skyWalkingDirectory := new File("$DEFAULT"),
    skyWalkingMirror := SkyWalkingDefaults.MIRROR,
    skyWalkingEnableDefaultActivations := true,
    skyWalkingEnableDefaultPlugins := true,
    skyWalkingEnableOptionalPlugins := false,
    skyWalkingEnableBootstrapPlugins := false,
    skyWalkingActivations := Seq.empty,
    skyWalkingPlugins := Seq.empty,
    skyWalkingPluginProjects := Seq.empty,
    skyWalkingConfigDirectory := baseDirectory.value / "conf/skywalking",

    skyWalkingDefaultDirectory := findDefaultDirectory.value,
    skyWalkingResolvedActivations := resolveActivations.value,
    skyWalkingResolvedPlugins := resolvePlugins.value,
    skyWalkingAssemblyPluginProjects := assemblyPluginProjects.value,
    skyWalkingResolvedPluginProjects := resolvePluginProjects.value,
    skyWalkingDownloadDistribution := skyWalkingDownloadTask.value,

    libraryDependencies ++= Seq() ++
      // Plugins
      skyWalkingPlugins.value
        .map(plugin => plugin.withConfigurations(configurations = Option(Provided.name))),
    resolvedJavaAgents ++= resolveJavaAgents.value,
    mappings in Universal ++= mappingJavaAgents.value,
    compile in Compile := (compile in Compile dependsOn ensureCompile).value,
    test in Test := (test in Test dependsOn ensureTest).value,
    clean := (clean dependsOn ensureClean).value,
  ) ++ inConfig(Universal)(Seq(
    stage := (stage dependsOn ensureStage).value,
    dist := (dist dependsOn ensureStage).value,
    packageZipTarball := (packageZipTarball dependsOn ensureStage).value,
  ))

  def findDefaultDirectory: Def.Initialize[Task[File]] = Def.task {
    val structure = buildStructure.value
    IO.asFile(structure.root.toURL) / s"tools/skywalking-${SkyWalkingDefaults.VERSION}"
  }

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
    if (hasConfig.value) {
      Def.task {
        skyWalkingDownloadDistribution.value
        assemblyPluginProjects.value
      }
    }
    else Def.task {
      assemblyPluginProjects.value
    }
  }

  def ensureClean: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (clean in p))
    }
    all
  }

  def ensureTest: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (test in p in Test))
    }
    all
  }

  def ensureCompile: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (compile in p in Compile))
    }
    all
  }

  private def hasConfig: Def.Initialize[Task[Boolean]] = Def.task[Boolean] {
    skyWalkingConfigDirectory.value.exists() && skyWalkingConfigDirectory.value.length() > 0
  }

  def resolveJavaAgents: Def.Initialize[Task[Seq[ResolvedAgent]]] = Def.taskDyn[Seq[ResolvedAgent]] {
    if (!hasConfig.value) {
      println("Skip adding SkyWalking as javaAgent due to no SkyWalking config")
      Def.task {
        Seq.empty
      }
    } else {
      val dir = resolveDirectory.value
      Def.task {
        Seq(
          ResolvedAgent(
            JavaAgent(SkyWalkingDefaults.GROUP_ID % SkyWalkingDefaults.ARTIFACT_ID % SkyWalkingDefaults.VERSION),
            dir / "agent/skywalking-agent.jar"
          )
        )
      }
    }
  }

  def resolveDirectory: Def.Initialize[Task[File]] = Def.taskDyn {
    val dir = skyWalkingDirectory.value
    if (dir.name == "$DEFAULT") {
      Def.task {
        skyWalkingDefaultDirectory.value
      }
    } else {
      Def.task {
        dir
      }
    }
  }

  def mappingJavaAgents: Def.Initialize[Task[Seq[(File, String)]]] = Def.taskDyn[Seq[(File, String)]] {
    if (!hasConfig.value) {
      Def.task {
        Seq.empty
      }
    } else {
      val dir = resolveDirectory.value
      Def.task {
        // config
        skyWalkingConfigDirectory.value.listFiles()
          .filter(file => file.isFile)
          .map(file => Tuple2(file, s"${SkyWalkingDefaults.ARTIFACT_ID}/config/" + file.name)) ++
          // default activations
          agentJarFiles(dir, "activations", "activations", skyWalkingEnableDefaultActivations.value) ++
          // custom activations
          resolveActivations.value
            .filter(plugin => plugin != null)
            .map(plugin => Tuple2(plugin.artifact, s"${SkyWalkingDefaults.ARTIFACT_ID}/activations/" + plugin.artifact.name)) ++
          // default plugins
          agentJarFiles(dir, "plugins", "plugins", skyWalkingEnableDefaultPlugins.value) ++
          // optional plugins
          agentJarFiles(dir, "optional-plugins", "plugins", skyWalkingEnableOptionalPlugins.value) ++
          // bootstrap plugins
          agentJarFiles(dir, "bootstrap-plugins", "plugins", skyWalkingEnableBootstrapPlugins.value) ++
          // custom plugins
          (resolvePlugins.value ++ resolvePluginProjects.value)
            .filter(plugin => plugin != null)
            .map(plugin => Tuple2(plugin.artifact, s"${SkyWalkingDefaults.ARTIFACT_ID}/plugins/" + plugin.artifact.name)) ++
          // custom plugin projects
//          resolvePluginProjects.value
//            .filter(plugin => plugin != null)
//            .map(plugin => Tuple2(plugin.artifact, s"${SkyWalkingDefaults.ARTIFACT_ID}/plugins/" + plugin.artifact.name)) ++
          Seq()
      }
    }
  }

  def resolveActivations: Def.Initialize[Task[Seq[ResolvedPlugin]]] = Def.task[Seq[ResolvedPlugin]] {
    skyWalkingActivations.value flatMap { plugin =>
      (update in Provided).value.matching(Modules.exactFilter(plugin)).headOption map {
        jar => ResolvedPlugin(plugin, jar)
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

  private def agentJarFiles(base: File, source: String, dest: String, enabled: Boolean): Seq[(File, String)] = {
    val dir = base / "agent" / source
    if (enabled && dir.exists() && dir.isDirectory) {
      dir.listFiles(jarFileFilter)
        .map(file => Tuple2(file, s"${SkyWalkingDefaults.ARTIFACT_ID}/$dest/" + file.name))
    } else {
      Seq()
    }
  }

  def skyWalkingDownloadTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val dir = resolveDirectory.value
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
