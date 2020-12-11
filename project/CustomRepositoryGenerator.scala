import sbt.{File, IO, fileToRichFile}

import java.net.URL
import scala.io.Source

object CustomRepositoryGenerator {
  def generateUpdatePlugisXml
  (
     pluginId: String,
     pluginVersion: String,
     pluginZipUrl: URL,
     ideaSinceBuild: String,
     ideaUntilBuild: String,
     outputDirectory: File
   ): Unit = {
    require(pluginZipUrl.getProtocol == "https", "The protocol for the plugin zip file URL must be https")

    val keyValuesToReplace = Map(
      "plugin_id" -> pluginId,
      "plugin_version" -> pluginVersion,
      "zip_url" -> pluginZipUrl.toString,
      "idea_since_build" -> ideaSinceBuild,
      "idea_until_build" -> ideaUntilBuild
    )

    val pluginsXmlContent = Source.fromResource("updatePlugins.template.xml", getClass.getClassLoader)
      .getLines()
      .map { line =>
        keyValuesToReplace.foldLeft(line) {
          case (currentLine, (keyToReplace, valueToReplace)) =>
            currentLine.replaceAllLiterally(s"{{${keyToReplace}}}", valueToReplace)
        }
      }
      .mkString("\n")

    IO.write(outputDirectory / "updatePlugins.xml", pluginsXmlContent)
  }
}
