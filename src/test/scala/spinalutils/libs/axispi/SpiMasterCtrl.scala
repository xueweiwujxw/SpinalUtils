package spinalutils.libs.axispi

import spinal.core._
import spinal.lib._

import spinal.sim._
import spinal.core.sim._
import spinal.lib.com.spi._

object SpiMasterCtrlSim extends App {
  SimConfig.withFstWave.doSim(
    SpiMasterCtrlContinuous(SpiMasterCtrlGenerics(ssWidth = 8, timerWidth = 8, dataWidth = 8))
  ) { dut =>
    dut.clockDomain.forkStimulus(10 MHz)

    // init
    dut.io.cmd.valid #= false

    dut.io.spi.miso #= true

    // config
    dut.io.config.kind.cpha #= false
    dut.io.config.kind.cpol #= false
    dut.io.config.sclkToggle #= 1
    dut.io.config.ss.activeHigh #= 0
    dut.io.config.ss.setup #= 1
    dut.io.config.ss.hold #= 1
    dut.io.config.ss.disable #= 1

    dut.clockDomain.waitSampling(10)

    // enable 0  args[4:1] index args[0] enable/disable
    dut.io.cmd.mode #= SpiMasterCtrlCmdMode.SS
    dut.io.cmd.args #= BigInt("0001", 2)
    dut.io.cmd.valid #= true
    dut.clockDomain.waitSampling()
    while (!dut.io.cmd.ready.toBoolean)
      dut.clockDomain.waitSampling()

    // spi send a5  args[8] r/w args[7:0] wdata
    dut.io.cmd.mode #= SpiMasterCtrlCmdMode.DATA
    dut.io.cmd.args #= BigInt("010100101", 2)
    dut.io.cmd.valid #= true
    dut.clockDomain.waitSampling()
    while (!dut.io.cmd.ready.toBoolean)
      dut.clockDomain.waitSampling()

    // spi send 66 and read  args[8] r/w args[7:0] wdata
    dut.io.cmd.mode #= SpiMasterCtrlCmdMode.DATA
    dut.io.cmd.args #= BigInt("101100110", 2)
    dut.io.cmd.valid #= true
    dut.clockDomain.waitSampling()
    while (!dut.io.cmd.ready.toBoolean)
      dut.clockDomain.waitSampling()

    // spi read  args[8] r/w args[7:0] wdata
    dut.io.cmd.mode #= SpiMasterCtrlCmdMode.DATA
    dut.io.cmd.args #= BigInt("100000000", 2)
    dut.io.cmd.valid #= true
    dut.clockDomain.waitSampling()
    while (!dut.io.cmd.ready.toBoolean)
      dut.clockDomain.waitSampling()

    // disable 0  args[4:1] index args[0] enable/disable
    dut.io.cmd.mode #= SpiMasterCtrlCmdMode.SS
    dut.io.cmd.args #= BigInt("0000", 2)
    dut.io.cmd.valid #= true
    dut.clockDomain.waitSampling()
    while (!dut.io.cmd.ready.toBoolean)
      dut.clockDomain.waitSampling()

    // finish
    dut.io.cmd.valid #= false

    dut.clockDomain.waitSampling(10)
  }
}
