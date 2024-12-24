package spinalutils.xilinx.ip

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba4.axilite._
import spinal.core.internals.Cast

object AxiDataWidthAdapterProtocol extends SpinalEnum(binarySequential) {
  val AXI4, AXI3, AXI4LITE = newElement()
  AXI4.setName("AXI4")
  AXI3.setName("AXI3")
  AXI4LITE.setName("AXI4LITE")
}

object AxiDataWidthAdapterRWMode extends SpinalEnum(binarySequential) {
  val READ_WRITE, READ_ONLY, WRITE_ONLY = newElement()
  READ_WRITE.setName("READ_WRITE")
  READ_ONLY.setName("READ_ONLY")
  WRITE_ONLY.setName("WRITE_ONLY")
}

object AxiDataWidthAdapterFifoMode extends SpinalEnum(binarySequential) {
  val FifoNone, PacketFifo, PacketFifoWithCC = newElement()
}

case class AxiDataWidthAdapterConfig(
    name: String,
    protocol: AxiDataWidthAdapterProtocol.E = AxiDataWidthAdapterProtocol.AXI4,
    rwmode: AxiDataWidthAdapterRWMode.E = AxiDataWidthAdapterRWMode.READ_WRITE,
    addressWidth: Int = 32,
    siDataWidth: Int = 128,
    miDataWidth: Int = 32,
    siIdWidth: Int = 0,
    maxBurstLenth: Int = 256,
    fifoMode: AxiDataWidthAdapterFifoMode.E = AxiDataWidthAdapterFifoMode.FifoNone,
    syncStages: Int = 3
) {
  private val dataWidths = Set(32, 64, 128, 256, 512, 1024)
  require(addressWidth >= 12 && addressWidth <= 64, "addressWidth must be between 12 and 64")
  require(dataWidths.contains(siDataWidth), f"siDataWidth must be one of $dataWidths")
  require(
    dataWidths.contains(miDataWidth) && siDataWidth != miDataWidth,
    f"siDataWidth must be one of $dataWidths and be different from miDataWidth $miDataWidth"
  )
  require(siIdWidth >= 0 && siIdWidth <= 32, "siIdWidth must be between 0 and 32")
  require(
    fifoMode == AxiDataWidthAdapterFifoMode.FifoNone || (fifoMode != AxiDataWidthAdapterFifoMode.FifoNone && siDataWidth < miDataWidth && protocol != AxiDataWidthAdapterProtocol.AXI4LITE),
    f"When fifoMode is not None, siDataWidth ($siDataWidth) must be less than miDataWidth ($miDataWidth) and protocol must be not $protocol"
  )
  require(syncStages >= 2 && syncStages <= 8, "syncStages must be between 2 and 8")
}

case class AxiDataWidthAdapter(conf: AxiDataWidthAdapterConfig) extends BlackBox with IXilinxIP {
  setDefinitionName(f"axi_dwidth_converter_${conf.name}")

  val io = new Bundle {
    val s_axi_lite = conf.protocol == AxiDataWidthAdapterProtocol.AXI4LITE generate slave(
      AxiLite4(
        AxiLite4Config(
          addressWidth = conf.addressWidth,
          dataWidth = conf.siDataWidth
        )
      )
    ).setPartialName("s_axi")
    val s_axi =
      conf.protocol == AxiDataWidthAdapterProtocol.AXI3 || conf.protocol == AxiDataWidthAdapterProtocol.AXI4 generate
        slave(
          Axi4(
            Axi4Config(
              addressWidth = conf.addressWidth,
              dataWidth = conf.siDataWidth,
              idWidth = conf.siIdWidth,
              withAxi3 = conf.protocol == AxiDataWidthAdapterProtocol.AXI3,
              useRegion = conf.protocol != AxiDataWidthAdapterProtocol.AXI3
            )
          )
        )

    val m_axi_lite = conf.protocol == AxiDataWidthAdapterProtocol.AXI4LITE generate master(
      AxiLite4(
        AxiLite4Config(
          addressWidth = conf.addressWidth,
          dataWidth = conf.miDataWidth
        )
      )
    ).setPartialName("m_axi")
    val m_axi =
      conf.protocol == AxiDataWidthAdapterProtocol.AXI3 || conf.protocol == AxiDataWidthAdapterProtocol.AXI4 generate
        master(
          Axi4(
            Axi4Config(
              addressWidth = conf.addressWidth,
              dataWidth = conf.miDataWidth,
              idWidth = -1,
              useId = false,
              withAxi3 = conf.protocol == AxiDataWidthAdapterProtocol.AXI3,
              useRegion = conf.protocol != AxiDataWidthAdapterProtocol.AXI3
            )
          )
        )

    val s_axi_aclk = in(Bool())
    val s_axi_aresetn = in(Bool())

    val m_axi_aclk = conf.fifoMode == AxiDataWidthAdapterFifoMode.PacketFifoWithCC generate in(Bool())
    val m_axi_aresetn = conf.fifoMode == AxiDataWidthAdapterFifoMode.PacketFifoWithCC generate in(Bool())
  }

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()

    val createCmd =
      f"""set axiDwidthConvExist [lsearch -exact [get_ips axi_dwidth_converter_*] axi_dwidth_converter_${conf.name}]
if { $$axiDwidthConvExist <0} {
  create_ip -name axi_dwidth_converter -vendor xilinx.com -library ip -version 2.1 -module_name axi_dwidth_converter_${conf.name}
}"""

    tcls = tcls :+ createCmd

    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    properties = properties :+ f"CONFIG.PROTOCOL {${conf.protocol}}"
    properties = properties :+ f"CONFIG.READ_WRITE_MODE {${conf.rwmode}}"
    properties = properties :+ f"CONFIG.ADDR_WIDTH {${conf.addressWidth}}"
    properties = properties :+ f"CONFIG.SI_DATA_WIDTH {${conf.siDataWidth}}"
    properties = properties :+ f"CONFIG.MI_DATA_WIDTH {${conf.miDataWidth}}"
    properties = properties :+ f"CONFIG.SI_ID_WIDTH {${conf.siIdWidth}}"
    properties = properties :+ f"CONFIG.MAX_SPLIT_BEATS {${conf.maxBurstLenth}}"
    properties = properties :+ f"CONFIG.FIFO_MODE {${conf.fifoMode.position}}"
    properties = properties :+ f"CONFIG.SYNCHRONIZATION_STAGES {${conf.syncStages}}"
    properties =
      properties :+ f"CONFIG.ACLK_ASYNC {${if (conf.fifoMode == AxiDataWidthAdapterFifoMode.PacketFifoWithCC) 1 else 0}}"

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips axi_dwidth_converter_${conf.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }

  noIoPrefix()
  if (io.s_axi != null)
    Axi4SpecRenamer(io.m_axi)
  if (io.m_axi != null)
    Axi4SpecRenamer(io.s_axi)
  if (io.s_axi_lite != null)
    AxiLite4SpecRenamer(io.s_axi_lite)
  if (io.m_axi_lite != null)
    AxiLite4SpecRenamer(io.m_axi_lite)
}
