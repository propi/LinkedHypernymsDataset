package cz.vse.lhd.mapreduce.cmd

import cz.vse.lhd.mapreduce.Logger

class HypenymExtractionCommand(
  startPointer : Int,
  endPointer : Int
) extends Command {

  def execute = {
    Logger.get.info(s"New extraction process have been started: ${startPointer} - ${endPointer}")
    MavenCommandReceiver(s"""scala:run -Dlauncher=runner -DaddArgs="module.properties|$startPointer|$endPointer" """.trim, HypenymExtractionCommand.homeDir)
    Logger.get.info(s"The extraction process have been finished: ${startPointer} - ${endPointer}")
  }
  
}

object HypenymExtractionCommand {
  
  val homeDir = "../HypernymExtractor"
  
}