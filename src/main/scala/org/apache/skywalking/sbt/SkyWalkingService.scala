package org.apache.skywalking.sbt

import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.resolvedJavaAgents
import com.lightbend.sbt.javaagent.JavaAgent.ResolvedAgent
import com.lightbend.sbt.javaagent.{JavaAgent, Modules}
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.{Universal, dist, packageZipTarball, stage}
import org.apache.skywalking.sbt.internal.{Downloader, Helper}
import sbt.Keys.{buildStructure, _}
import sbt.{Def, File, ModuleID, settingKey, task, taskKey, _}
import sbtassembly.AssemblyKeys._

case class ResolvedPlugin(plugin: ModuleID, artifact: File)

object SkyWalkingServiceKeys {
  val skyWalkingEnableDefaultActivations = settingKey[Boolean]("Enable default activations. (default: true)")
  val skyWalkingEnableDefaultPlugins = settingKey[Boolean]("Enable default plugins. (default: true)")
  val skyWalkingEnableOptionalPlugins = settingKey[Boolean]("Enable optional plugins. (default: false)")
  val skyWalkingEnableBootstrapPlugins = settingKey[Boolean]("Enable bootstrap plugins. (default: false)")
  val skyWalkingActivations = settingKey[Seq[ModuleID]]("The custom skyWalking activations")
  val skyWalkingActivationProjects = settingKey[Seq[ProjectReference]]("The custom skyWalking activation projects")
  val skyWalkingPlugins = settingKey[Seq[ModuleID]]("The custom skyWalking plugins")
  val skyWalkingPluginProjects = settingKey[Seq[ProjectReference]]("The custom skyWalking plugin projects")
  val skyWalkingConfigDirectory = settingKey[File]("The agent config directory")

  val skyWalkingResolvedActivations = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking activations")
  val skyWalkingResolvedActivationProjects = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking activation projects")
  val skyWalkingResolvedPlugins = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking plugins")
  val skyWalkingResolvedPluginProjects = taskKey[Seq[ResolvedPlugin]]("The resolved custom skyWalking plugin projects")

  val skyWalkingDownload = settingKey[Boolean]("Whether download SkyWalking distribution. (default: false)")
  val skyWalkingDownloadDirectory = settingKey[File](s"The directory of SkyWalking.")
  val skyWalkingDownloadMirror = settingKey[String](s"The mirror of apache download site. (default ${SkyWalkingDefaults.MIRROR})")
  val skyWalkingDownloadUrl = settingKey[String](s"The remote url of SkyWalking agent. (default: ${SkyWalkingDefaults.MIRROR}/${SkyWalkingDefaults.VERSION}/apache-skywalking-java-agent-${SkyWalkingDefaults.VERSION}.tgz)")
  val skyWalkingDownloadDistribution = taskKey[Unit]("Download SkyWalking distribution if required.")
}

object SkyWalkingService extends AutoPlugin {
  override def requires: Plugins = SkyWalkingBase && JavaAgent && UniversalPlugin

  val autoImport: SkyWalkingServiceKeys.type = SkyWalkingServiceKeys

  val skyWalkingModule = settingKey[ModuleID]("The skyWalking module")

  import SkyWalkingKeys._
  import SkyWalkingServiceKeys._
  import com.typesafe.sbt.packager.{ Keys => PackagerKeys }

  private val DEFAULT_DIRECTORY_TAG = "$DEFAULT"

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    //    skyWalkingVersion := SkyWalkingDefaults.VERSION,
    skyWalkingModule := SkyWalkingDefaults.GROUP_ID % SkyWalkingDefaults.ARTIFACT_ID % skyWalkingVersion.value,

    // Basic settings
    skyWalkingEnableDefaultActivations := true,
    skyWalkingEnableDefaultPlugins := true,
    skyWalkingEnableOptionalPlugins := false,
    skyWalkingEnableBootstrapPlugins := false,
    skyWalkingActivations := Seq.empty,
    skyWalkingActivationProjects := Seq.empty,
    skyWalkingPlugins := Seq.empty,
    skyWalkingPluginProjects := Seq.empty,
    skyWalkingConfigDirectory := baseDirectory.value / "conf/skywalking",

    // Basic tasks
    skyWalkingResolvedActivations := resolveActivations.value,
    skyWalkingResolvedActivationProjects := resolveActivationProjects.value,
    skyWalkingResolvedPlugins := resolvePlugins.value,
    skyWalkingResolvedPluginProjects := resolvePluginProjects.value,

    // Download related
    skyWalkingDownload := false,
    skyWalkingDownloadDirectory := new File(DEFAULT_DIRECTORY_TAG),
    skyWalkingDownloadMirror := SkyWalkingDefaults.MIRROR,
    skyWalkingDownloadUrl := "",
    skyWalkingDownloadDistribution := downloadDistribution.value,

    // Java agent
    libraryDependencies ++= Seq() ++
      // Activations && Plugins
      (skyWalkingActivations.value ++ skyWalkingPlugins.value)
        .map(plugin => plugin.withConfigurations(configurations = Option(Provided.name))),
    projectDependencies ++= Def.task {
      if (hasAgentJar.value) Seq()
      else Seq(skyWalkingModule.value % Provided)
    }.value,
    resolvedJavaAgents ++= resolveJavaAgents.value,
    Universal / mappings ++= mappingJavaAgents.value,

    // Disable agent if no agent.config
    // PackagerKeys.bashScriptExtraDefines := PackagerKeys.bashScriptExtraDefines.value map (define =>
    //   if (define.contains("skywalking-agent")) {
    //     println("[[ -e \"${app_home}/../skywalking-agent/agent.config\" ]] && " + define)
    //     "[[ -e \"${app_home}/../skywalking-agent/agent.config\" ]] && " + define
    //   } else {
    //     println(define)
    //     define
    //   }
    // ),

    // Common tasks
    Compile / compile := ((Compile / compile) dependsOn ensureCompile).value,
    Test / test := ((Test / test) dependsOn ensureTest).value,
    clean := (clean dependsOn ensureClean).value,
  ) ++ inConfig(Universal)(Seq(
    // Universal tasks
    stage := (stage dependsOn ensureStage).value,
    dist := (dist dependsOn ensureStage).value,
    packageZipTarball := (packageZipTarball dependsOn ensureStage).value,
  ))

  def ensureCompile: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingActivationProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / Compile / compile))
    }
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / Compile / compile))
    }
    all
  }

  def ensureTest: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingActivationProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / Test / test))
    }
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / Test / test))
    }
    all
  }

  def ensureClean: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingActivationProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / clean))
    }
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / clean))
    }
    all
  }

  def ensureStage: Def.Initialize[Task[Unit]] = Def.taskDyn[Unit] {
    if (hasConfig.value) {
      if (shouldDownload.value) {
        ensureAssembly dependsOn downloadDistribution
      } else {
        ensureAssembly
      }
    }
    else ensureAssembly
  }

  def ensureAssembly: Def.Initialize[Task[Unit]] = Def.taskDyn {
    var all = Def.task {}
    val structure = buildStructure.value
    skyWalkingActivationProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / assembly))
    }
    skyWalkingPluginProjects.value foreach { ref =>
      structure.allProjectRefs
        .find(p => p.project == ref.asInstanceOf[LocalProject].project)
        .foreach(p => all = all dependsOn (p / assembly))
    }
    all
  }

  private def shouldDownload: Def.Initialize[Boolean] = Def.setting {
    skyWalkingDownload.value && (
      skyWalkingEnableDefaultActivations.value ||
        skyWalkingEnableDefaultPlugins.value ||
        skyWalkingEnableOptionalPlugins.value ||
        skyWalkingEnableBootstrapPlugins.value
      )
  }

  private def hasAgentJar: Def.Initialize[Task[Boolean]] = Def.task {
    val agentJar = resolveDownloadDirectory.value / "skywalking-agent.jar"
    shouldDownload.value || (agentJar.exists() && agentJar.isFile)
  }

  private def hasConfig: Def.Initialize[Task[Boolean]] = Def.task[Boolean] {
    skyWalkingConfigDirectory.value.exists() &&
      skyWalkingConfigDirectory.value.listFiles().exists(file => file.isFile)
  }

  def resolveJavaAgents: Def.Initialize[Task[Seq[ResolvedAgent]]] = Def.taskDyn[Seq[ResolvedAgent]] {
    if (!hasConfig.value) {
      println("Skip adding SkyWalking as javaAgent due to no SkyWalking config")
      Def.task {
        Seq.empty
      }
    } else {
      val agentJar = resolveDownloadDirectory.value / "skywalking-agent.jar"
      val agentModule = skyWalkingModule.value
      if (agentJar.exists() && agentJar.isFile) {
        Def.task {
          Seq(
            ResolvedAgent(JavaAgent(agentModule, SkyWalkingDefaults.AGENT_NAME), agentJar)
          )
        }
      } else {
        Def.task {
          Seq(
            ((Provided / update).value.matching(Modules.exactFilter(agentModule)).headOption map {
              jar => ResolvedAgent(JavaAgent(agentModule, SkyWalkingDefaults.AGENT_NAME), jar)
            }).get
          )
        }
      }
    }
  }

  def mappingJavaAgents: Def.Initialize[Task[Seq[(File, String)]]] = Def.taskDyn[Seq[(File, String)]] {
    if (!hasConfig.value) {
      Def.task {
        Seq.empty
      }
    } else {
      val source = resolveDownloadDirectory.value
      val target = SkyWalkingDefaults.AGENT_NAME
      Def.task {
        // config
        skyWalkingConfigDirectory.value.listFiles()
          .filter(file => file.isFile)
          .map(file => Tuple2(file, s"$target/config/${file.name}")) ++
          // default activations
          agentJarFiles(source, "activations", s"$target/activations", skyWalkingDownload.value && skyWalkingEnableDefaultActivations.value) ++
          // extra activations
          (resolveActivations.value ++ resolveActivationProjects.value)
            .filter(plugin => plugin != null)
            .map(plugin => Tuple2(plugin.artifact, s"$target/activations/${plugin.artifact.name}")) ++
          // default plugins
          agentJarFiles(source, "plugins", s"$target/plugins", skyWalkingDownload.value && skyWalkingEnableDefaultPlugins.value) ++
          // optional plugins
          agentJarFiles(source, "optional-plugins", s"$target/plugins", skyWalkingDownload.value && skyWalkingEnableOptionalPlugins.value) ++
          // bootstrap plugins
          agentJarFiles(source, "bootstrap-plugins", s"$target/plugins", skyWalkingDownload.value && skyWalkingEnableBootstrapPlugins.value) ++
          // extra plugins
          (resolvePlugins.value ++ resolvePluginProjects.value)
            .filter(plugin => plugin != null)
            .map(plugin => Tuple2(plugin.artifact, s"$target/plugins/${plugin.artifact.name}")) ++
          Seq()
      }
    }
  }

  def resolveActivations: Def.Initialize[Task[Seq[ResolvedPlugin]]] = Def.task[Seq[ResolvedPlugin]] {
    skyWalkingActivations.value flatMap { plugin =>
      (Provided / update).value.matching(Modules.exactFilter(plugin)).headOption map {
        jar => ResolvedPlugin(plugin, jar)
      }
    }
  }

  def resolveActivationProjects: Def.Initialize[Task[Seq[ResolvedPlugin]]] = Def.taskDyn[Seq[ResolvedPlugin]] {
    val stateTask = state.taskValue
    val structure = buildStructure.value
    val artTasks: Seq[Task[Seq[ResolvedPlugin]]] = skyWalkingActivationProjects.value flatMap { ref =>
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

  def resolvePlugins: Def.Initialize[Task[Seq[ResolvedPlugin]]] = Def.task[Seq[ResolvedPlugin]] {
    skyWalkingPlugins.value flatMap { plugin =>
      (Provided / update).value.matching(Modules.exactFilter(plugin)).headOption map {
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
      val module: ModuleID = extracted.get(ref / projectID)
      val assemblyOutputPathTask = extracted.getOpt(ref / assembly / assemblyOutputPath).orNull
      for {
        assemblyOutputPath <- assemblyOutputPathTask
      } yield {
        Seq(
          ResolvedPlugin(module, assemblyOutputPath)
        )
      }
    }

  private def agentJarFiles(base: File, source: String, dest: String, enabled: Boolean): Seq[(File, String)] = {
    val dir = base / source
    if (enabled && dir.exists() && dir.isDirectory) {
      dir.listFiles(Helper.jarFileFilter).map(file => Tuple2(file, dest + "/" + file.name))
    } else {
      Seq()
    }
  }

  def downloadDistribution: Def.Initialize[Task[Unit]] = Def.task {
    val dir = resolveDownloadDirectory.value
    if (!dir.exists() || dir.length() == 0) {
      var url = if (skyWalkingDownloadUrl.value.length == 0) {
        var mirror = skyWalkingDownloadMirror.value
        mirror = if (mirror.endsWith("/")) mirror.substring(0, mirror.length - 1) else mirror
        s"$mirror/$version/apache-skywalking-java-agent-$version.tgz"
      } else {
        skyWalkingDownloadUrl.value
      }
      Downloader.download(url, dir)
    }
  }

  def getDefaultDirectory: Def.Initialize[Task[File]] = Def.task {
    val structure = buildStructure.value
    IO.asFile(structure.root.toURL) / s"tools/skywalking-${SkyWalkingDefaults.VERSION}"
  }

  def resolveDownloadDirectory: Def.Initialize[Task[File]] = Def.taskDyn {
    val dir = skyWalkingDownloadDirectory.value
    if (dir.name == DEFAULT_DIRECTORY_TAG) {
      Def.task {
        getDefaultDirectory.value
      }
    } else {
      Def.task {
        dir
      }
    }
  }
}
