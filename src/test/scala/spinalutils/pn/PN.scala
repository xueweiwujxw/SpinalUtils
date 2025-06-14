package spinalutils.pn

import spinal.core._
import spinal.lib._

import spinal.core.sim._
import spinal.sim._
import java.nio.file.Paths
import java.io._
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

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

object PNDetectExample extends App {
  SpinalConfig()
    .generateVerilog(
      PNDetect(order = 23, poly = 0x800021, inWidth = 8)
    )
    .printPruned()
}

object PNGenExample extends App {
  SpinalConfig().generateVerilog(
    new PNGen(
      order = 23,
      init = 1,
      poly = 0x800021,
      outWidth = 32
    )
  )
}

object PNGenSim extends App {
  SimConfig.withIVerilog.doSim(
    new PNGen(
      order = 23,
      init = 1,
      poly = 0x800021,
      outWidth = 32
    )
  ) { dut =>
    val pnfile = new File("pntest23.dat")
    val outputStream = new DataOutputStream(new FileOutputStream(pnfile))
    var correct = new Array[Byte](dut.io.m_stream.payload.getBitsWidth / 8)
    val random = new Random
    val threshold = 0.8
    dut.clockDomain.forkStimulus(10)

    dut.io.en #= false
    dut.io.flush #= false
    dut.io.m_stream.ready #= false

    dut.clockDomain.waitSampling(20)

    dut.io.en #= true

    val ready_for = fork {
      for (i <- 0 until 5000000) {
        dut.clockDomain.waitSampling()
        val randomValue = random.nextDouble()
        if (randomValue <= threshold)
          dut.io.m_stream.ready #= true
        else
          dut.io.m_stream.ready #= false
      }
    }

    for (i <- 0 until 5000000) {
      dut.clockDomain.waitSampling()
      if (dut.io.m_stream.valid.toBoolean && dut.io.m_stream.ready.toBoolean) {
        var bytes = dut.io.m_stream.payload.toBigInt.toByteArray

        if (bytes.size > correct.size) {
          for (i <- 1 until correct.size + 1)
            correct(i - 1) = bytes(i)
        } else if (bytes.size == correct.size) {
          for (i <- 0 until correct.size)
            correct(i) = bytes(i)
        } else {
          for (i <- (correct.size - bytes.size) until correct.size)
            correct(i) = bytes(i - (correct.size - bytes.size))
          for (i <- 0 until (correct.size - bytes.size))
            correct(i) = 0
        }
        outputStream.write(correct)
      }
    }

    dut.io.flush #= true

    dut.clockDomain.waitSampling(20)

    dut.io.flush #= false
    dut.io.en #= false

    dut.clockDomain.waitSampling(20)
    ready_for.join()
    outputStream.close()
  }
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
