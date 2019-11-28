package play.api.mq.kafka

import java.util.function.{Consumer => JConsumer, Function => JFunction}
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.libs.Json

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Try}

@Singleton
class Kafka @Inject() (conf:Configuration,
                       system: ActorSystem,
                       implicit val mt:Materializer,
                       implicit val ec: ExecutionContext){

  private val logger = LoggerFactory.getLogger(classOf[Kafka])
  private val convert:Converter = new JsonConverter()

  lazy val producerSettings:ProducerSettings[String,Array[Byte]] = {
    val kafkaServers = conf.get[String]("akka.kafka.servers")
    val producerSettings = ProducerSettings(system, new StringSerializer , new ByteArraySerializer)
      .withBootstrapServers(kafkaServers)
    producerSettings
  }

  //lazy val commitBatch = conf.getOptional[Int]("play.kafka.consumer.commit_batch").getOrElse(1)

  lazy val consumerSettings:ConsumerSettings[String,Array[Byte]] = {
    val kafkaServers = conf.get[String]("akka.kafka.servers")
    val consumerSettings = ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(kafkaServers)

    consumerSettings
  }

  def errorLogger[T](f: Try[T]): Unit = f match  {
    case Failure(t) => logger.error("Kafka occur error",t)
    case _ =>
  }

  def publish(topics:String,msg:AnyRef):Unit = {
    Source.single(msg)
          .map(m => new ProducerRecord[String, Array[Byte]](topics,convert.toByteArray(msg)))
          .runWith(Producer.plainSink(producerSettings)).onComplete(errorLogger)
  }

  def subscribe[T:ClassTag](topics:String,func: T=> Unit):Unit = {
    Consumer.committableSource(consumerSettings,Subscriptions.topics(topics)).map(msg => {
      val t = implicitly[ClassTag[T]].runtimeClass
      val m = convert.fromByteArray(t,msg.record.value())
      val typedMsg = m.asInstanceOf[T]
      func(typedMsg)
      msg.committableOffset.commitScaladsl() //每一次都commit,可以做处理了多条消息批量提交的
    }).runWith(Sink.ignore).onComplete(errorLogger)
  }

  def subscribe[T](topics:String,clazz:Class[T],func: T=> Unit):Unit = {
    Consumer.committableSource(consumerSettings,Subscriptions.topics(topics)).map(msg => {
      val typedMsg = convert.fromByteArray(clazz,msg.record.value())
      func(typedMsg)
      msg.committableOffset.commitScaladsl() //每一次都commit,可以做处理了多条消息批量提交的
    }).runWith(Sink.ignore).onComplete(errorLogger)
  }

  def subscribeSink[T](topics:String,clazz:Class[T],func: T=> ProducerRecord[String,AnyRef]):Unit = {
    Consumer.committableSource(consumerSettings,Subscriptions.topics(topics)).map(msg => {
      val typedMsg = convert.fromByteArray(clazz,msg.record.value())
      val restMsg = func(typedMsg)
      ProducerMessage.Message(new ProducerRecord[String, Array[Byte]](
        restMsg.topic(),
        restMsg.partition(),
        restMsg.key(),
        convert.toByteArray(restMsg.value()),
        restMsg.headers()
      ), msg.committableOffset) //每一次都commit,可以做处理了多条消息批量提交的
    }).runWith(Producer.commitableSink(producerSettings)).onComplete(errorLogger)
  }

  def subscribe[T](topics:String,clazz:Class[T],func:JConsumer[T]):Unit = {
    Consumer.committableSource(consumerSettings,Subscriptions.topics(topics)).map(msg => {
      val typedMsg = convert.fromByteArray(clazz,msg.record.value())
      func.accept(typedMsg)
      msg.committableOffset.commitScaladsl() //每一次都commit,可以做处理了多条消息批量提交的
    }).runWith(Sink.ignore).onComplete(errorLogger)
  }

  def subscribe[T](topics:String,clazz:Class[T],func:JFunction[T,ProducerRecord[String,AnyRef]]):Unit = {
    Consumer.committableSource(consumerSettings,Subscriptions.topics(topics)).map(msg => {
      val typedMsg = convert.fromByteArray(clazz,msg.record.value())
      val restMsg = func.apply(typedMsg)
      ProducerMessage.Message(new ProducerRecord[String, Array[Byte]](
        restMsg.topic(),
        restMsg.partition(),
        restMsg.key(),
        convert.toByteArray(restMsg.value()),
        restMsg.headers()
      ), msg.committableOffset) //每一次都commit,可以做处理了多条消息批量提交的
    }).runWith(Producer.commitableSink(producerSettings)).onComplete(errorLogger)
  }
}


trait Converter{

  def toByteArray(v:AnyRef):Array[Byte]

  def fromByteArray[T](clazz:Class[T],b:Array[Byte]):T
}

private class JsonConverter extends Converter{
  override def toByteArray(v: AnyRef):Array[Byte] = {
    Json.stringify(Json.toJson(v)).getBytes("UTF-8")
  }

  override def fromByteArray[T](clazz: Class[T],b:Array[Byte]):T = {
    Json.fromJson(Json.parse(b),clazz)
  }
}