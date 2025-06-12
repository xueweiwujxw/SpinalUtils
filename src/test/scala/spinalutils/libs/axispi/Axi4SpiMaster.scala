package spinalutils.libs.axispi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.com.spi._

object Axi4SpiMasterGen extends App {
  SpinalVerilog(
    Axi4SpiMaster(
      config = Axi4SpiMasterConfig(
        axiConfig = Axi4Config(
          addressWidth = 12,
          dataWidth = 32,
          useBurst = true,
          idWidth = 4,
          useRegion = false,
          useProt = true,
          useCache = true,
          useLock = true,
          useQos = false
        ),
        spiConfig = SpiMasterCtrlGenerics(
          ssWidth = 8,
          timerWidth = 4,
          dataWidth = 8
        )
      )
    )
  )
}

import spinal.sim._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi.sim._

object Axi4SpiMasterSim extends App {
  SimConfig.withFstWave.doSim(
    Axi4SpiMaster(
      config = Axi4SpiMasterConfig(
        axiConfig = Axi4Config(
          addressWidth = 12,
          dataWidth = 32,
          useBurst = true,
          idWidth = 4,
          useRegion = false,
          useProt = true,
          useCache = true,
          useLock = true,
          useQos = false
        ),
        spiConfig = SpiMasterCtrlGenerics(
          ssWidth = 8,
          timerWidth = 4,
          dataWidth = 8
        )
      )
    )
  ) { dut =>
    dut.io.m_spi.miso #= false
    dut.io.s_axi.ar.valid #= false
    dut.io.s_axi.aw.valid #= false
    dut.io.s_axi.w.valid #= false

    dut.clockDomain.forkStimulus(20 MHz)
    val axiMaster = Axi4Master(dut.io.s_axi, dut.clockDomain, "Axi4SpiMaster")

    var threadRunning = true
    val ssAllTrue = (BigInt(1) << dut.config.spiConfig.ssWidth) - 1

    def hasSS = dut.io.m_spi.ss.toBigInt != ssAllTrue

    val misoThread = fork {
      while (threadRunning) {
        dut.clockDomain.waitSamplingWhere(hasSS || !threadRunning)
        var i = 0
        while (threadRunning && hasSS) {
          dut.io.m_spi.miso #= i % 2 == 1
          if (hasSS) {
            dut.clockDomain.waitRisingEdgeWhere(dut.io.m_spi.sclk.toBoolean || !hasSS)
            if (hasSS)
              i += 1
          }
        }
      }
    }

    dut.clockDomain.waitSampling(10)

    println("disable cmd en")
    axiMaster.write(0x1c, List(0x00.toByte, 0, 0, 0))

    println("set cpha as 0 and cpol as 0")
    axiMaster.write(8, List(0), burst = Axi4Bursts.Fixed)
    println("set sclkToggle as 0")
    axiMaster.write(0xc, List(0), burst = Axi4Bursts.Fixed)
    println("set ssSetup as 0")
    axiMaster.write(0x10, List(0), burst = Axi4Bursts.Fixed)
    println("set ssHold as 0")
    axiMaster.write(0x14, List(0), burst = Axi4Bursts.Fixed)
    println("set ssDisable as 0")
    axiMaster.write(0x18, List(4), burst = Axi4Bursts.Fixed)
    println("enable cmd intr amd rsp intr")
    axiMaster.write(0x4, List(0x03), burst = Axi4Bursts.Fixed)

    println("first spi transfer")

    println("enable chip 1")
    axiMaster.write(0, List(0x01.toByte, 0, 0, 0x11.toByte))

    println("write 0xa5 to spi")
    axiMaster.write(0, List(0xa5.toByte, 0, 0, 0x00.toByte))

    println("write 0x96 to spi and read from spi")
    axiMaster.write(0, List(0x96.toByte, 0, 0, 0x01.toByte))

    println("write 0x00 to spi and read from spi")
    axiMaster.write(0, List(0x00.toByte, 0, 0, 0x01.toByte))

    println("disable chip 0")
    axiMaster.write(0, List(0x01.toByte, 0, 0, 0x10.toByte))

    println("enable cmd en")
    axiMaster.write(0x1c, List(0x01.toByte))
    dut.clockDomain.waitSamplingWhere(dut.io.m_spi.ss.toBigInt == 0xff)
    println("disable cmd en")
    axiMaster.write(0x1c, List(0x00.toByte))

    println("read status")
    axiMaster.read(4, 4)

    println("read data")
    axiMaster.read(0, 1)
    println("read data")
    axiMaster.read(0, 1)

    dut.clockDomain.waitSampling(50)

    println("second spi transfer")

    println("enable chip 1")
    axiMaster.write(0, List(0x01.toByte, 0, 0, 0x11.toByte))

    println("write 0xa5 to spi")
    axiMaster.write(0, List(0xa5.toByte, 0, 0, 0x00.toByte))

    println("write 0x96 to spi and read from spi")
    axiMaster.write(0, List(0x96.toByte, 0, 0, 0x01.toByte))

    println("write 0x00 to spi and read from spi")
    axiMaster.write(0, List(0x00.toByte, 0, 0, 0x01.toByte))

    println("disable chip 0")
    axiMaster.write(0, List(0x01.toByte, 0, 0, 0x10.toByte))

    println("enable cmd en")
    axiMaster.write(0x1c, List(0x01.toByte))
    dut.clockDomain.waitSamplingWhere(dut.io.m_spi.ss.toBigInt == 0xff)
    println("disable cmd en")
    axiMaster.write(0x1c, List(0x00.toByte))

    println("read status")
    axiMaster.read(4, 4)

    println("read data")
    axiMaster.read(0, 1)
    println("read data")
    axiMaster.read(0, 1)

    axiMaster.read(0x20, 4)

    dut.clockDomain.waitSampling(50)

    threadRunning = false
    misoThread.join()
  }
}
