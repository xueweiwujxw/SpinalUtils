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

object PNSim extends App {
  SimConfig.withFstWave.doSim(
    PNSimWrapper(order = 23, init = 1, poly = 0x800021, width = 256)
  ) { dut =>
    dut.clockDomain.forkStimulus(10)

    dut.io.en #= false
    dut.io.flush #= false

    var running = true

    val check_thread = fork {
      while (running) {
        dut.clockDomain.waitSampling()
        assert(dut.io.errcnt.toBigInt == 0)
      }
    }

    dut.clockDomain.waitSampling(20)

    dut.io.en #= true

    dut.clockDomain.waitSampling(5000)

    dut.io.flush #= true

    dut.clockDomain.waitSampling(20)

    dut.io.flush #= false
    dut.io.en #= false

    running = false
    check_thread.join()

    dut.clockDomain.waitSampling(20)
  }
}
