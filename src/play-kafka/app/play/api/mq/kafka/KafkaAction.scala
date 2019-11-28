package play.api.mq.kafka

import javax.inject.{Inject, Singleton}

import play.api.routes.Router


@Singleton
class KafkaAction @Inject() (kafka: Kafka,router:Router) {

  def startup():Unit = {

  }
}
