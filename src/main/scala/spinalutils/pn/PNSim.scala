package spinalutils.pn

import spinal.core._
import spinal.lib._

import spinal.core.sim._
import spinal.sim._

case class PNSimWrapper(order: Int, init: BigInt, poly: BigInt, width: Int) extends Component {
  val io = new Bundle {
    val en = in(Bool())
    val flush = in(Bool())
    val errcnt = out(UInt(64 bits))
  }

  val gen = PNGen(order = order, init = init, poly = poly, outWidth = width)
  val detect = PNDetect(order = order, poly = poly, inWidth = width)

  gen.io.en := io.en
  gen.io.flush := io.flush

  detect.io.flush := io.flush
  io.errcnt := detect.io.errcnt

  gen.io.m_stream >> detect.io.s_stream
}
