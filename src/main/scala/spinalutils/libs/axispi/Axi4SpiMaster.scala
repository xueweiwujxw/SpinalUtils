package spinalutils.libs.axispi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.com.spi._
import spinal.lib.bus.amba4.axilite._

case class Axi4SpiMasterConfig(
    axiConfig: Axi4Config,
    spiConfig: SpiMasterCtrlGenerics,
    cmdFifoDepth: Int = 32,
    rspFifoDepth: Int = 32,
    continuous: Boolean = false
)

case class Axi4SpiMaster(config: Axi4SpiMasterConfig) extends Component {
  val io = new Bundle {
    val s_axi = slave(Axi4(config.axiConfig))
    val m_spi = master(SpiMaster(config.spiConfig.ssWidth))
    val intr = out(Bool())
  }

  val spiMaster = SpiMasterCtrlContinuous(config.spiConfig, config.continuous)
  io.m_spi <> spiMaster.io.spi

  val axiLite = AxiLite4Utils.Axi4Rich(io.s_axi).toLite()

  val bus = AxiLite4SlaveFactory(axiLite)

  val bridge = spiMaster.io.driveFrom(bus = bus, baseAddress = 0)(generics =
    SpiMasterCtrlMemoryMappedConfig(
      config.spiConfig,
      config.cmdFifoDepth,
      config.rspFifoDepth
    )
  )
  io.intr := bridge.interruptCtrl.interrupt
  bus.printDataModel()

  noIoPrefix()
  Axi4SpecRenamer(io.s_axi)
}
