package play.api.assets

import java.io.{File, InputStream}

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.Future

trait AssetsStore {

  def link(file:File):String

  def getSink(fileName :String,isTmp:Boolean):(String,Sink[ByteString, Future[NotUsed]])

  def getSink(fileName:String):(String,Sink[ByteString, Future[NotUsed]])

  def save(in :InputStream):String  //path

  def save(fileName :String,in :InputStream):String  //path

  def asyncSave(fileName :String,in :InputStream):Future[String]  //path

  def delete(path :String):Boolean  //path

  def getSource(path :String):Source[ByteString, NotUsed]

  def at(path:String): Action[AnyContent]

  def getURI(path:String):String

  def getWorkdir():File
}
