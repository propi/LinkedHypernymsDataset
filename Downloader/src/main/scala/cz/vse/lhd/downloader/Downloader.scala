package cz.vse.lhd.downloader

import java.io._
import java.net.URL

import cz.vse.lhd.core.{Dir, AppConf, ConfGlobal}
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.jena.riot.system.StreamRDFWriter
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import org.slf4j.LoggerFactory

object Downloader extends AppConf {

  val logger = LoggerFactory.getLogger(getClass)

  object Conf extends ConfGlobal {

    sealed trait ConfData

    case class AutoConfData(downloadBaseUrl: String, ontologyBaseUrl: String, version: String) extends ConfData

    case class ManualConfData(ontology: String,
                              instanceTypes: String,
                              instanceTypesEn: String,
                              instanceTypesTransitive: String,
                              instanceTypesTransitiveEn: String,
                              interlanguageLinksEn: String,
                              disambiguations: String,
                              shortAbstracts: String) extends ConfData

    val globalPropertiesFile = AppConf.args(0)

    lazy val confData = if (config.opt[Boolean]("LHD.Downloader.manual").exists(_ == true)) {
      ManualConfData(
        config.get[String]("LHD.Downloader.ontology-url"),
        config.get[String]("LHD.Downloader.instance-types-url"),
        config.get[String]("LHD.Downloader.instance-types-en-url"),
        config.get[String]("LHD.Downloader.instance-types-transitive-url"),
        config.get[String]("LHD.Downloader.instance-types-transitive-en-url"),
        config.get[String]("LHD.Downloader.interlanguage-links-en-url"),
        config.get[String]("LHD.Downloader.disambiguations-url"),
        config.get[String]("LHD.Downloader.short-abstracts-url")
      )
    } else {
      val downloadBaseUrl = config.get[String]("LHD.Downloader.base-url") /: Dir
      AutoConfData(downloadBaseUrl, downloadBaseUrl + config.get[String]("LHD.Downloader.ontology-dir") /: Dir, dbpediaVersion)
    }
  }

  val datasetDownloader = {

    trait FileDownloaderImpl extends FileDownloader {

      private def formatToTargetFileExtension(datasetFormat: DatasetFormat) = datasetFormat match {
        case OWL => OWL.fileExtension
        case _ => "nt"
      }

      def downloadFile(source: URL, dataset: Dataset, targetName: String) = {
        val target = new File(Conf.datasetsDir + targetName + "." + formatToTargetFileExtension(dataset.format))
        target.getParentFile match {
          case x: File if x.isDirectory && x.canWrite =>
          case x: File if !x.isDirectory => if (!x.mkdirs || !x.canWrite) throw new DownloaderException(s"Directory ${x.getAbsolutePath} couldn't be created!")
          case _ => throw new DownloaderException(s"Bad parent folder of ${target.getAbsolutePath}!")
        }
        if (target.isFile && !target.delete) throw new DownloaderException(s"File ${target.getAbsolutePath} cannot be deleted!")

        val bis = new InputStream {
          val is = new BufferedInputStream(dataset.compression match {
            case Some(BZ2) => new BZip2CompressorInputStream(source.openStream(), true)
            case Some(GZ) => new GzipCompressorInputStream(source.openStream(), true)
            case None => source.openStream()
          })
          var i = 0L
          var time = System.currentTimeMillis

          def read(): Int = {
            i = i + 1
            if (i % 10000000 == 0) {
              val speed = ((10 / ((System.currentTimeMillis - time) / 1000.0)) * 1000).round
              logger.info(s"The file: ${target.getName} is downloading... ${i / 1000000}MB downloaded (speed: ${speed}kB/s).")
              time = System.currentTimeMillis
            }
            is.read()
          }

          override def close(): Unit = is.close()
        }
        val bos = new BufferedOutputStream(new FileOutputStream(target))

        logger.info(s"The file: ${target.getName} is downloading...")
        try {
          if (dataset.format == OWL) {
            Stream.continually(bis.read()).takeWhile(_ != -1).foreach(bos.write)
          } else {
            val destination = StreamRDFWriter.getWriterStream(bos, RDFFormat.NTRIPLES_ASCII)
            RDFDataMgr.parse(destination, bis, dataset.format)
          }
        } finally {
          bos.close()
          bis.close()
        }
        logger.info(s"The file: ${target.getName} is completely downloaded.")
      }
    }

    Conf.confData match {
      case confData: Conf.AutoConfData => if (Conf.lang == "de") new DatasetDownloaderDe(confData) with FileDownloaderImpl else new DatasetDownloaderGlobal(confData) with FileDownloaderImpl
      case confData: Conf.ManualConfData => new DatasetDownloaderManual(confData) with FileDownloaderImpl
    }

  }

  datasetDownloader.download()

}

class DownloaderException(m: String) extends Exception(m)