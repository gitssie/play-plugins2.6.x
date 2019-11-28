package play.api.rpc.thrift

import akka.util.ByteString
import org.apache.thrift.transport.{TTransport, TTransportException}

class TByteStringTransport(val buf:ByteString) extends TTransport{

  override def read(buf: Array[Byte], off: Int, len: Int):Int = {
    val bytesRemaining: Int = this.buf.length - off
    val amtToRead: Int = if (len > bytesRemaining) {
      bytesRemaining
    }else {
      len
    }
    if (amtToRead > 0) {
      this.buf.copyToArray(buf, off, amtToRead)
    }
    amtToRead
  }

  override def isOpen = true

  override def close() = {}

  override def write(buf: Array[Byte], off: Int, len: Int) = {
    this.buf ++ (buf.slice(off,len))
  }

  override def open() = {}

  @throws[TTransportException]
  override def readAll(buf: Array[Byte], off: Int, len: Int): Int = {
    val bytesRemaining = this.buf.length - off
    if (len > bytesRemaining) throw new TTransportException("unexpected end of frame")
    this.buf.copyToArray(buf, off, len)
    len
  }

  override def getBuffer: Array[Byte] = buf.toArray
}
