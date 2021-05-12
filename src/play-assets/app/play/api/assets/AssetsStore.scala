package play.api.assets

import java.io.{File, InputStream}

import com.google.common.io.{ByteSink, ByteSource}
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.Future

trait AssetsStore {

  def link(file:File):String

  def getSink(fileName :String,isTmp:Boolean):(String,ByteSink)

  def save(in :InputStream):String  //path

  def save(fileName :String,in :InputStream):String  //path

  def asyncSave(fileName :String,in :InputStream):Future[String]  //path

  def delete(path :String):Boolean  //path

  def getSource(path :String):ByteSource  //path

  def at(path:String): Action[AnyContent]

  def getURI(path:String):String

  def getWorkdir():File
}
