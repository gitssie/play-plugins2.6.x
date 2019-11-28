package play.api.trace

object ZipkinTraceConfig {
  val AkkaName = "play.trace.zipkin.context"
  val ServiceName = "play.trace.service-name"
  val ZipkinBaseUrl = "play.trace.zipkin.base-url"
  val ZipkinSampleRate = "play.trace.zipkin.sample-rate"
}