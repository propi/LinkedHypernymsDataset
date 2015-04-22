package cz.vse.lhd.hypernymextractor

import cz.vse.lhd.core.AppConf
import cz.vse.lhd.core.BasicFunction._
import cz.vse.lhd.core.ConfGlobal
import cz.vse.lhd.core.FileExtractor
import java.io.File
import java.io.IOException
import cz.vse.lhd.core.Dir
import java.net.HttpURLConnection
import java.net.URL

import org.slf4j.LoggerFactory

import scala.io.Source

object Conf extends ConfGlobal {

  val globalPropertiesFile = AppConf.args(0).replaceFirst( """^["]""", "")

  val (
    gateDir,
    gatePluginLhdDir,
    gateJapeGrammar,
    memcachedAddress,
    memcachedPort,
    wikiApi,
    corpusSizePerThread
    ) = {
    (
      config.get[String]("LHD.HypernymExtractor.gate.dir") /: Dir,
      config.get[String]("LHD.HypernymExtractor.gate.plugin-lhd-dir") /: Dir,
      config.get[String]("LHD.HypernymExtractor.gate.jape-grammar"),
      config.get[String]("LHD.HypernymExtractor.memcached.address"),
      config.get[String]("LHD.HypernymExtractor.memcached.port"),
      config.get[String]("LHD.HypernymExtractor.wiki-api"),
      config.getOrElse("LHD.HypernymExtractor.corpus-size-per-thread", 10000)
      )
  }

  val (
    datasetShort_abstractsPath,
    datasetDisambiguations) = (
    s"${Conf.datasetsDir}short_abstracts_$lang.nt",
    s"${Conf.datasetsDir}disambiguations_$lang.nt"
    )

  lazy val datasetSize = tryClose(Source.fromFile(Conf.datasetShort_abstractsPath))(_.getLines().size)

  List(gateJapeGrammar, datasetShort_abstractsPath) foreach {
    case FileExtractor(_) =>
    case x => throw new IOException(s"File $x does not exist or is not writable.")
  }

  {
    val of = new File(Conf.outputDir)
    if (!of.isDirectory) of.mkdir
    val conn = new URL(wikiApi).openConnection.asInstanceOf[HttpURLConnection]
    try {
      conn.connect()
      if (conn.getResponseCode != HttpURLConnection.HTTP_OK)
        throw new IOException(s"WikiAPI $wikiApi is not reachable.")
    } finally {
      conn.disconnect()
    }
  }

}

class ProcessStatus private(step: Int, end: Int) {

  lazy val logger = LoggerFactory.getLogger(getClass)

  def this(end: Int) = this(0, end)

  def ++ = new ProcessStatus(step + 1, end)

  def plusplus = ++

  def tryPrint() = if (step % 1000 == 0)
    logger.info(s"$step of $end resources extracted: " + ((step.toDouble / end) * 100).round + "%")
}