package spinalutils.xilinx.ip

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axis._
import spinal.lib.bus.amba4.axi._
import spinal.lib.blackbox.xilinx.s7._
import spinalutils.libs.renamer.NameProcess._

object MoveNumberToEnd {
  def apply[T <: Data](that: Stream[T]): Stream[T] = {
    def doIt = {
      that.flatten.foreach((bt) => {
        bt.setName(replaceAndMoveToEnd("_\\d+".r, bt.getName()))
      })
    }
    if (Component.current == that.component)
      that.component.addPrePopTask(() => { doIt })
    else
      doIt

    that
  }
}

object EnumScale extends Enumeration {
  val Kilobytes = Value("Kilobytes")
  val Megabytes = Value("Megabytes")
  val Gigabytes = Value("Gigabytes")
  val Terabytes = Value("Terabytes")
  val Petabytes = Value("Petabytes")
  val Exabytes = Value("Exabytes")
}

object EnumInterrupt extends Enumeration {
  val INTA = Value("INTA")
  val INTB = Value("INTB")
  val INTC = Value("INTC")
  val INTD = Value("INTD")
  val NONE = Value("NONE")
}

object EnumDevice extends Enumeration {
  val vx690 = Value("xc7vx690tffg1158-2")
  val vu9p = Value("xcvu9p-flgb2104-2-i")
}

abstract class XdmaBase() extends BlackBox {}

/**
 * xdma ip core config
 *
 * @param name
 *   xdma ip core instantiation name
 * @param advancedMode
 *   enable advanced mode
 * @param lanes
 *   initial maximum link width of device
 * @param speed
 *   initial maximum link speed
 * @param refclock
 *   the frequency of the reference clock provided on sys_clk
 * @param dataWidth
 *   the axi data width
 * @param dataClock
 *   the axi data clock
 * @param h2cStreams
 *   number of dma read channel
 * @param c2hStreams
 *   number of dma write channel
 * @param bypass
 *   host access to user logic via AXI Memory Map interface
 * @param bypassSize
 *   AXI Memory Map interface size
 * @param bypassScale
 *   AXI Memory Map interface scale Kilobytes | Megabytes | Gigabytes |
 *   Terabytes | Petabytes | Exabytes
 * @param idWidth
 *   AXI ID Width
 * @param readChannelIds
 *   number of request ids for read channel
 * @param writeChannelIds
 *   number of request ids for write channel
 * @param userIrq
 *   indicated number of usert interrupt requests
 * @param interruptType
 *   legacy interrupt setting
 * @param location
 *   pcie block location
 */
case class XdmaConfig(
    name: String = "0",
    advancedMode: Boolean = true,
    lanes: Int = 1,
    speed: Double = 2.5,
    refclock: String = "125",
    dataWidth: Int = 256,
    dataClock: Int = 125,
    h2cStreams: Int = 1,
    c2hStreams: Int = 1,
    bypass: Boolean = true,
    bypassSize: Int = 4,
    bypassScale: EnumScale.Value = EnumScale.Megabytes,
    idWidth: Int = 4,
    readChannelIds: Int = 64,
    writeChannelIds: Int = 16,
    userIrq: Int = 16,
    interruptType: EnumInterrupt.Value = EnumInterrupt.NONE,
    location: String = ""
) {
  require(lanes >= 1 && lanes <= 16 && Integer.bitCount(lanes) == 1)
  require(speed == 2.5 || speed == 5.0 || speed == 8.0)
  require(refclock == "100" || refclock == "125" || refclock == "250")
  require(h2cStreams <= 4 && h2cStreams >= 1)
  require(c2hStreams <= 4 && c2hStreams >= 1)
  require(bypassSize >= 1 && bypassSize <= 512 && Integer.bitCount(bypassSize) == 1)
  require(idWidth == 4 || idWidth == 2)
  require(readChannelIds >= 1 && readChannelIds <= 64 && Integer.bitCount(readChannelIds) == 1)
  require(writeChannelIds >= 1 && writeChannelIds <= 32 && Integer.bitCount(writeChannelIds) == 1)
  require(userIrq <= 16 && userIrq >= 1)
}

case class Xdmavx690(config: XdmaConfig = XdmaConfig()) extends XdmaBase with IXilinxIP {
  setDefinitionName(f"xdma_${config.name}")
  val axis_config =
    Axi4StreamConfig(dataWidth = config.dataWidth / 8, useKeep = true, useLast = true)
  val axi_config = Axi4Config(
    addressWidth = 64,
    dataWidth = config.dataWidth,
    useBurst = true,
    idWidth = config.idWidth,
    useRegion = false,
    useProt = true,
    useCache = true,
    useLock = true,
    useQos = false
  )

  val io = new Bundle {
    val s_axis_c2h = Vec(slave(Axi4Stream(axis_config)), config.c2hStreams)
    val m_axis_h2c = Vec(master(Axi4Stream(axis_config)), config.h2cStreams)

    val m_axib = (config.bypass) generate master(Axi4(axi_config))
    val pci_exp_txn = out(Bits(config.lanes bits))
    val pci_exp_txp = out(Bits(config.lanes bits))
    val pci_exp_rxn = in(Bits(config.lanes bits))
    val pci_exp_rxp = in(Bits(config.lanes bits))

    val sys_clk = in(Bool())
    val sys_rst_n = in(Bool())

    val user_lnk_up = out(Bool())
    val axi_aclk = out(Bool())
    val axi_aresetn = out(Bool())

    val usr_irq_req = in(Bits(config.userIrq bits))
    val usr_irq_ack = out(Bits(config.userIrq bits))

    val msi_enable = out(Bool())
    val msix_enable = out(Bool())
    val msi_vector_width = out(Bits(3 bits))
  }

  noIoPrefix()
  Axi4SpecRenamer(io.m_axib)
  io.s_axis_c2h.map(x => MoveNumberToEnd(Axi4SpecRenamer(x)))
  io.m_axis_h2c.map(x => MoveNumberToEnd(Axi4SpecRenamer(x)))

  override def generateTcl(): List[String] = {
    var tcls: List[String] = List()
    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    val createCmd = f"""set xdmaExist [lsearch -exact [get_ips xdma_*] xdma_${config.name}]
if { $$xdmaExist <0} {
  create_ip -name xdma -vendor xilinx.com -library ip -version 4.1 -module_name xdma_${config.name}
}"""
    tcls = tcls :+ createCmd

    properties = properties :+ f"CONFIG.mode_selection {${if (config.advancedMode) "Advanced"
      else "Basic"}}"
    properties = properties :+ f"${if (config.location != "") f"CONFIG.pcie_blk_locn {${config.location}}"
      else ""}"
    properties = properties :+ f"CONFIG.pl_link_cap_max_link_width {X${config.lanes}}"
    properties = properties :+ f"CONFIG.pl_link_cap_max_link_speed {${config.speed}%.1f_GT/s}"
    properties = properties :+ f"CONFIG.ref_clk_freq {${config.refclock}_MHz}"
    properties = properties :+ f"CONFIG.axi_data_width {${config.dataWidth}_bit}"
    properties = properties :+ f"CONFIG.axisten_freq {${config.dataClock}}"
    properties = properties :+ f"CONFIG.axist_bypass_en {${config.bypass}}"
    properties = properties :+ f"CONFIG.axist_bypass_size {${config.bypassSize}}"
    properties = properties :+ f"CONFIG.axist_bypass_scale {${config.bypassScale}}"
    properties = properties :+ f"CONFIG.pf0_interrupt_pin {${config.interruptType}}"
    properties = properties :+ f"CONFIG.xdma_num_usr_irq {${config.userIrq}}"
    properties = properties :+ f"CONFIG.xdma_rnum_chnl {${config.h2cStreams}}"
    properties = properties :+ f"CONFIG.xdma_wnum_chnl {${config.c2hStreams}}"
    properties = properties :+ f"CONFIG.xdma_rnum_rids {${config.readChannelIds}}"
    properties = properties :+ f"CONFIG.xdma_wnum_rids {${config.writeChannelIds}}"
    // properties = properties :+ f"CONFIG.select_quad {GTY_Quad_233}" // has been disabled
    // properties = properties :+ f"CONFIG.plltype {QPLL1}" // has been disabled
    properties = properties :+ f"CONFIG.xdma_axi_intf_mm {AXI_Stream}"
    properties = properties :+ f"CONFIG.pf0_msix_enabled {true}"
    properties = properties :+ f"CONFIG.pf0_msix_cap_table_size {01F}"
    properties = properties :+ f"CONFIG.pf0_msix_cap_table_offset {00008000}"
    properties = properties :+ f"CONFIG.pf0_msix_cap_pba_offset {00008FE0}"
    properties = properties :+ f"CONFIG.cfg_mgmt_if {false}"
    properties = properties :+ f"CONFIG.axi_bypass_64bit_en {true}"
    properties = properties :+ f"CONFIG.axi_bypass_prefetchable {true}"
    properties = properties :+ f"CONFIG.pf0_device_id {9028}"
    properties = properties :+ f"CONFIG.PF0_DEVICE_ID_mqdma {9028}"
    properties = properties :+ f"CONFIG.PF2_DEVICE_ID_mqdma {9228}"
    properties = properties :+ f"CONFIG.PF3_DEVICE_ID_mqdma {9328}"
    properties = properties :+ f"CONFIG.PF0_SRIOV_VF_DEVICE_ID {A038}"
    properties = properties :+ f"CONFIG.PF1_SRIOV_VF_DEVICE_ID {A138}"
    properties = properties :+ f"CONFIG.PF2_SRIOV_VF_DEVICE_ID {A238}"
    properties = properties :+ f"CONFIG.PF3_SRIOV_VF_DEVICE_ID {A338}"

    properties.foreach { x =>
      if (x != "") {
        property = property.concat(x) + " "
      }
    }
    property = property.concat(f"] [get_ips xdma_${config.name}]\n")
    tcls :+ property
  }

  override def generateXdc(): List[String] = {
    var xdcs: List[String] = List()
    xdcs
  }

}

case class Xdmavu9p(config: XdmaConfig = XdmaConfig()) extends XdmaBase with IXilinxIP {
  setDefinitionName(f"xdma_${config.name}")
  val axis_config =
    Axi4StreamConfig(dataWidth = config.dataWidth / 8, useKeep = true, useLast = true)
  val axi_config = Axi4Config(
    addressWidth = 64,
    dataWidth = config.dataWidth,
    useBurst = true,
    idWidth = config.idWidth,
    useRegion = false,
    useProt = true,
    useCache = true,
    useLock = true,
    useQos = false
  )

  val io = new Bundle {
    val s_axis_c2h = Vec(slave(Axi4Stream(axis_config)), config.c2hStreams)
    val m_axis_h2c = Vec(master(Axi4Stream(axis_config)), config.h2cStreams)

    val m_axib = (config.bypass) generate master(Axi4(axi_config))
    val pci_exp_txn = out(Bits(config.lanes bits))
    val pci_exp_txp = out(Bits(config.lanes bits))
    val pci_exp_rxn = in(Bits(config.lanes bits))
    val pci_exp_rxp = in(Bits(config.lanes bits))

    val sys_clk = in(Bool())
    val sys_rst_n = in(Bool())
    val sys_clk_gt = in(Bool())

    val user_lnk_up = out(Bool())
    val axi_aclk = out(Bool())
    val axi_aresetn = out(Bool())

    val usr_irq_req = in(Bits(config.userIrq bits))
    val usr_irq_ack = out(Bits(config.userIrq bits))

    val msi_enable = out(Bool())
    val msix_enable = out(Bool())
    val msi_vector_width = out(Bits(3 bits))
  }

  noIoPrefix()
  Axi4SpecRenamer(io.m_axib)
  io.s_axis_c2h.map(x => MoveNumberToEnd(Axi4SpecRenamer(x)))
  io.m_axis_h2c.map(x => MoveNumberToEnd(Axi4SpecRenamer(x)))

  override def generateTcl(): List[String] = {
    var tcls: List[String] = List()
    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    val createCmd = f"""set xdmaExist [lsearch -exact [get_ips xdma_*] xdma_${config.name}]
if { $$xdmaExist <0} {
  create_ip -name xdma -vendor xilinx.com -library ip -version 4.1 -module_name xdma_${config.name}
}"""
    tcls = tcls :+ createCmd

    properties = properties :+ f"CONFIG.mode_selection {${if (config.advancedMode) "Advanced"
      else "Basic"}}"
    properties = properties :+ f"${if (config.location != "") f"CONFIG.pcie_blk_locn {${config.location}}"
      else ""}"
    properties = properties :+ f"CONFIG.pl_link_cap_max_link_width {X${config.lanes}}"
    properties = properties :+ f"CONFIG.pl_link_cap_max_link_speed {${config.speed}%.1f_GT/s}"
    properties = properties :+ f"CONFIG.ref_clk_freq {${config.refclock}_MHz}"
    properties = properties :+ f"CONFIG.axi_data_width {${config.dataWidth}_bit}"
    properties = properties :+ f"CONFIG.axisten_freq {${config.dataClock}}"
    properties = properties :+ f"CONFIG.axist_bypass_en {${config.bypass}}"
    properties = properties :+ f"CONFIG.axist_bypass_size {${config.bypassSize}}"
    properties = properties :+ f"CONFIG.axist_bypass_scale {${config.bypassScale}}"
    properties = properties :+ f"CONFIG.pf0_interrupt_pin {${config.interruptType}}"
    properties = properties :+ f"CONFIG.xdma_num_usr_irq {${config.userIrq}}"
    properties = properties :+ f"CONFIG.xdma_rnum_chnl {${config.h2cStreams}}"
    properties = properties :+ f"CONFIG.xdma_wnum_chnl {${config.c2hStreams}}"
    properties = properties :+ f"CONFIG.xdma_rnum_rids {${config.readChannelIds}}"
    properties = properties :+ f"CONFIG.xdma_wnum_rids {${config.writeChannelIds}}"
    properties = properties :+ f"CONFIG.select_quad {GTY_Quad_233}"
    // properties = properties :+ f"CONFIG.plltype {QPLL1}"
    properties = properties :+ f"CONFIG.xdma_axi_intf_mm {AXI_Stream}"
    properties = properties :+ f"CONFIG.pf0_msix_enabled {true}"
    properties = properties :+ f"CONFIG.pf0_msix_cap_table_size {01F}"
    properties = properties :+ f"CONFIG.pf0_msix_cap_table_offset {00008000}"
    properties = properties :+ f"CONFIG.pf0_msix_cap_pba_offset {00008FE0}"
    properties = properties :+ f"CONFIG.cfg_mgmt_if {false}"
    properties = properties :+ f"CONFIG.axi_bypass_64bit_en {true}"
    properties = properties :+ f"CONFIG.axi_bypass_prefetchable {true}"
    properties = properties :+ f"CONFIG.pf0_device_id {9028}"
    properties = properties :+ f"CONFIG.PF0_DEVICE_ID_mqdma {9028}"
    properties = properties :+ f"CONFIG.PF2_DEVICE_ID_mqdma {9228}"
    properties = properties :+ f"CONFIG.PF3_DEVICE_ID_mqdma {9328}"
    properties = properties :+ f"CONFIG.PF0_SRIOV_VF_DEVICE_ID {A038}"
    properties = properties :+ f"CONFIG.PF1_SRIOV_VF_DEVICE_ID {A138}"
    properties = properties :+ f"CONFIG.PF2_SRIOV_VF_DEVICE_ID {A238}"
    properties = properties :+ f"CONFIG.PF3_SRIOV_VF_DEVICE_ID {A338}"

    properties.foreach { x =>
      if (x != "") {
        property = property.concat(x) + " "
      }
    }
    property = property.concat(f"] [get_ips xdma_${config.name}]\n")
    tcls :+ property
  }

  override def generateXdc(): List[String] = {
    var xdcs: List[String] = List()
    xdcs
  }

}

object xdma {
  def apply[T <: XdmaBase](
      config: XdmaConfig = XdmaConfig(),
      deviceType: EnumDevice.Value
  ): T = {
    deviceType match {
      case EnumDevice.vu9p  => Xdmavu9p(config).asInstanceOf[T]
      case EnumDevice.vx690 => Xdmavx690(config).asInstanceOf[T]
      case _                => null.asInstanceOf[T]
    }
  }
}
