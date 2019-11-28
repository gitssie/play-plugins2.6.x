package play.api.trace

/**
 * Class for a trace data take over between requests.
 * @param span the present span
 */
case class TraceData(span: brave.Span)
