package spinalutils.xilinx.ip.aurora

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axis._
import spinal.lib.bus.amba4.axi.Axi4SpecRenamer
import spinalutils.libs.renamer.NameProcess._
import scala.util.matching.Regex
import spinalutils.libs.signal._
import spinalutils.libs.renamer._
import spinalutils.xilinx.ip.IXilinxIP

object Aurora8b10bDataFlowMode extends SpinalEnum(binarySequential) {
  val DUPLEX, RXONLY, TXONLY = newElement()
  DUPLEX.setName("Duplex")
  RXONLY.setName("RX-only Simplex")
  TXONLY.setName("TX-only Simplex")
}

object Aurora8b10bInterfaceMode extends SpinalEnum(binarySequential) {
  val FRAMING, STREAMING = newElement()
  FRAMING.setName("Framing")
  STREAMING.setName("Streaming")
}

object Aurora8b10bFlowCtrlMode extends SpinalEnum(binarySequential) {
  val NONE, UFC, IMMEDIATE_NFC, COMPLETION_NFC, UFC_IMMEDIATE_NFC, UFC_COMPLETION_NFC = newElement()
  NONE.setName("None")
  UFC.setName("UFC")
  IMMEDIATE_NFC.setName("Immediate NFC")
  COMPLETION_NFC.setName("Completion NFC")
  UFC_IMMEDIATE_NFC.setName("UFC+ Immediate NFC")
  UFC_COMPLETION_NFC.setName("UFC+ Completion NFC")
}

case class LaneLoc(postion: Int = 1, setValue: String = "1") {
  require(postion >= 1 && postion <= 16)
  require(
    (setValue.matches("^\\d+$") && setValue.toInt >= 1 && setValue.toInt <= 16) || setValue == "X"
  )
  def valid = setValue.matches("^\\d+$") && setValue.toInt >= 1 && setValue.toInt <= 16
}

case class Aurora8b10bConfig(
    name: String = "inst",
    laneWidth: Int = 4,
    lineRate: Double = 3.125,
    gtRefClk: String = "125.000",
    initClk: Double = 50.0,
    drpClk: Double = 50.0,
    dataFlow: Aurora8b10bDataFlowMode.E = Aurora8b10bDataFlowMode.DUPLEX,
    interface: Aurora8b10bInterfaceMode.E = Aurora8b10bInterfaceMode.FRAMING,
    flowCtrl: Aurora8b10bFlowCtrlMode.E = Aurora8b10bFlowCtrlMode.NONE,
    scramber: Boolean = false,
    littleEndian: Boolean = false,
    crc: Boolean = false,
    laneLoc: List[LaneLoc] = List(LaneLoc(1, "1")),
    incore: Boolean = false,
    initClkSingleEnd: Boolean = false,
    gtRefClkSingleEnd: Boolean = false,
    gtClock: List[String]
    // TODO add specific devices
) {
  def streamMode = interface == Aurora8b10bInterfaceMode.STREAMING
  def frameMode = interface == Aurora8b10bInterfaceMode.FRAMING
  def hasTx = dataFlow != Aurora8b10bDataFlowMode.RXONLY
  def hasRx = dataFlow != Aurora8b10bDataFlowMode.TXONLY
  def hasUFC =
    flowCtrl == Aurora8b10bFlowCtrlMode.UFC ||
      flowCtrl == Aurora8b10bFlowCtrlMode.UFC_IMMEDIATE_NFC ||
      flowCtrl == Aurora8b10bFlowCtrlMode.UFC_COMPLETION_NFC
  def hasNFC =
    flowCtrl == Aurora8b10bFlowCtrlMode.IMMEDIATE_NFC ||
      flowCtrl == Aurora8b10bFlowCtrlMode.COMPLETION_NFC ||
      flowCtrl == Aurora8b10bFlowCtrlMode.UFC_IMMEDIATE_NFC ||
      flowCtrl == Aurora8b10bFlowCtrlMode.UFC_COMPLETION_NFC

  require(laneWidth == 2 || laneWidth == 4, "lane width supports 2 bytes or 4 bytes")
  require(lineRate >= 0.5 && lineRate <= 6.6, "line rates supports [0.5, 6.6] Gbps")
  require(initClk >= 50.0 && initClk <= 125.0, "init clk frequency supports [50.0, 125.0] MHz")
  require(initClk >= 50.0 && initClk <= 175.01, "init clk frequency supports [50.0, 175.01] MHz")
  require(laneLoc.length >= 1 && laneLoc.length <= 16, "lanes number supports [1, 16]")

  if (streamMode)
    require(flowCtrl == Aurora8b10bFlowCtrlMode.NONE, "stream mode not supports flow control")

  if (dataFlow != Aurora8b10bDataFlowMode.DUPLEX)
    require(
      flowCtrl == Aurora8b10bFlowCtrlMode.NONE || flowCtrl == Aurora8b10bFlowCtrlMode.UFC,
      "simplex mode only supports None and UFC flow control method"
    )

  require(gtClock.length >= 1 && gtClock.length <= 2, "gt clocks num supports [1,2]")

  def lanes = laneLoc.map(f => f.valid.toInt).reduceBalancedTree(_ + _)

  def axisConf =
    Axi4StreamConfig(dataWidth = laneWidth * lanes, useKeep = frameMode, useLast = frameMode)
  def ufcTxAxisConf = Axi4StreamConfig(dataWidth = 3)
  def ufcRxAxisConf =
    Axi4StreamConfig(
      dataWidth = scala.math.min(laneWidth * lanes, 16),
      useKeep = frameMode,
      useLast = frameMode
    )
  def nfcTxAxisConf = Axi4StreamConfig(dataWidth = 4)
  def nfcRxAxisConf = Axi4StreamConfig(dataWidth = 4)

  def drpNum = lanes
  def qpllClkNum = scala.math.ceil(lanes.toDouble / 4.0).toInt

  def initSingleEnd = !incore || initClkSingleEnd
  def gtSingleEnd = !incore || gtRefClkSingleEnd
}

protected case class DrpPort(num: Int = 1) extends Bundle with IMasterSlave {
  val clk_in = Bool()
  val addr_in = Vec(Bits(9 bits), num)
  val en_in = Vec(Bool(), num)
  val di_in = Vec(Bits(16 bits), num)
  val rdy_out = Vec(Bool(), num)
  val do_out = Vec(Bits(16 bits), num)
  val we_in = Vec(Bool(), num)

  def asMaster(): Unit = {
    out(clk_in)
    for (i <- 0 until num) {
      out(addr_in(i), en_in(i), di_in(i), we_in(i))
      in(rdy_out(i), do_out(i))
    }
  }

  def specRenamer: Unit = {
    def changebt[T <: Data](bt: T, i: Int) {
      bt.setName(replaceAndMoveToEnd(this.getName().concat("_").r, bt.getName(), true))
      if (i > 0)
        bt.setName(replaceAndMoveToEnd("\\d+".r, bt.getName().concat("lane")))
      else
        bt.setName(replaceAndMoveToEnd("_\\d+".r, bt.getName(), true))
      bt.setName(this.getName().concat(bt.getName()))
    }

    def doIt = {
      this.clk_in.setName("drpclk_in")
      this.addr_in.zipWithIndex.foreach { case (bt, i) => changebt(bt, i) }
      this.en_in.zipWithIndex.foreach { case (bt, i) => changebt(bt, i) }
      this.di_in.zipWithIndex.foreach { case (bt, i) => changebt(bt, i) }
      this.rdy_out.zipWithIndex.foreach { case (bt, i) => changebt(bt, i) }
      this.do_out.zipWithIndex.foreach { case (bt, i) => changebt(bt, i) }
      this.we_in.zipWithIndex.foreach { case (bt, i) => changebt(bt, i) }
    }
    if (Component.current == this.component)
      this.component.addPrePopTask(() => { doIt })
    else
      doIt
  }
}

protected case class QPLLPort(incore: Boolean = false, locs: List[LaneLoc])
    extends Bundle
    with IMasterSlave {
  var parts: Set[Int] = Set()
  locs.foreach(f => {
    if (f.setValue != "X") {
      parts += f.postion / 4 + 1
    }
  })

  def num = parts.size
  def partsArr = parts.toArray

  require(num >= 1 && num <= 4)

  val qplllock_out = incore generate Vec(Bool(), num)
  val qpllrefclklost_out = incore generate Vec(Bool(), num)
  val qpllclk_quad_out = incore generate Vec(Bool(), num)
  val qpllrefclk_quad_out = incore generate Vec(Bool(), num)

  val qplllock_in = !incore generate Vec(Bool(), num)
  val qpllrefclklost_in = !incore generate Vec(Bool(), num)
  val qpllclk_quad_in = !incore generate Vec(Bool(), num)
  val qpllrefclk_quad_in = !incore generate Vec(Bool(), num)

  val qpllreset_out = !incore generate Vec(Bool(), num)

  def asMaster(): Unit = {
    for (i <- 0 until num) {
      if (!incore) {
        out(
          qplllock_in(i),
          qpllrefclklost_in(i),
          qpllclk_quad_in(i),
          qpllrefclk_quad_in(i)
        )
        in(qpllreset_out(i))
      } else {
        in(qplllock_out(i), qpllrefclklost_out(i), qpllclk_quad_out(i), qpllrefclk_quad_out(i))
      }
    }
  }

  def specRenamer: Unit = {
    def changebt[T <: Data](pattern: Regex, bt: T, append: String) {
      bt.setName(
        replaceAndMoveToEnd(pattern, bt.getName(), true).concat(append)
      )
    }

    def doIt = {
      if (!incore) {
        qplllock_in.foreach(bt => changebt("_qplllock_in_".r, bt, "_qplllock_in"))
        qpllrefclklost_in.foreach(bt => changebt("_qpllrefclklost_in_".r, bt, "_qpllrefclklost_in"))
        qpllclk_quad_in.zipWithIndex.foreach { case (bt, i) =>
          changebt("_in_\\d+".r, bt, partsArr(i).toString().concat("_in"))
        }
        qpllrefclk_quad_in.zipWithIndex.foreach { case (bt, i) =>
          changebt("_in_\\d+".r, bt, partsArr(i).toString().concat("_in"))
        }
        qpllreset_out.foreach(bt => changebt("_qpllreset_out_".r, bt, "_qpllreset_out"))
      } else {
        qplllock_out.foreach(bt => changebt("_qplllock_out_".r, bt, "_qplllock_out"))
        qpllrefclklost_out.foreach(bt =>
          changebt("_qpllrefclklost_out_".r, bt, "_qpllrefclklost_out")
        )
        qpllclk_quad_out.zipWithIndex.foreach { case (bt, i) =>
          changebt("_out_\\d+".r, bt, partsArr(i).toString().concat("_out"))
        }
        qpllrefclk_quad_out.zipWithIndex.foreach { case (bt, i) =>
          changebt("_out_\\d+".r, bt, partsArr(i).toString().concat("_out"))
        }
      }

    }
    if (Component.current == this.component)
      this.component.addPrePopTask(() => { doIt })
    else
      doIt
  }
}

protected case class GtRefClkPort(num: Int = 1, singleEnd: Boolean = false)
    extends Bundle
    with IMasterSlave {
  val refclk = singleEnd generate Vec(Bool(), num)
  val refclk_diff = !singleEnd generate Vec(DiffSignal(), num).setPartialName("refclk")
  val refclk_out = !singleEnd generate Vec(Bool(), num)

  def asMaster(): Unit = {
    for (i <- 0 until num) {
      if (singleEnd) {
        out(refclk(i))
      } else {
        out(refclk_diff(i))
        in(refclk_out(i))
      }
    }
  }

  def specRenamer: Unit = {
    def changebt[T <: Data](pattern: Regex, bt: T, append: String) {
      bt.setName(
        replaceAndMoveToEnd(pattern, bt.getName(), true).concat(append)
      )
    }

    def doIt = {
      if (singleEnd) {
        refclk.zipWithIndex.foreach { case (bt, i) =>
          changebt("_\\d+".r, bt, (i + 1).toString)
        }
      } else {
        refclk_diff.zipWithIndex.foreach { case (bt, i) =>
          changebt("_\\d+".r, bt, (i + 1).toString())
        }
        refclk_out.zipWithIndex.foreach { case (bt, i) =>
          changebt("_out_\\d+".r, bt, (i + 1).toString.concat("_out"))
        }
      }
    }
    if (Component.current == this.component)
      this.component.addPrePopTask(() => { doIt })
    else
      doIt
  }
}

case class Aurora8b10bZynq(config: Aurora8b10bConfig) extends BlackBox with IXilinxIP {
  setDefinitionName(f"aurora_8b10b_${config.name}")
  val io = new Bundle {

    /** AXI TX Interface */
    val s_axi_tx = config.hasTx generate slave(Axi4Stream(config.axisConf))

    /** AXI RX Interface */
    val m_axi_rx = config.hasRx generate master(Flow(Axi4Stream.Axi4StreamBundle(config.axisConf)))

    /** Flow Control TX Interface */
    val s_axi_nfc_tx =
      config.hasTx && config.hasNFC generate slave(Stream(Bits(4 bits)))
    val s_axi_ufc_tx =
      config.hasTx && config.hasUFC generate slave(Stream(Bits(3 bits)))

    /** Flow Control RX Interface */
    val m_axi_nfc_rx = config.hasRx && config.hasNFC generate master(
      Flow(Bits(4 bits))
    )
    val m_axi_ufc_rx = config.hasRx && config.hasUFC generate master(
      Flow(Axi4Stream.Axi4StreamBundle(config.ufcRxAxisConf))
    )

    /** GT Serial I/O */
    val rx = in(DiffSignal(config.lanes))
    val tx = out(DiffSignal(config.lanes))

    /** GT Reference Clock Interface */
    val gt_refclk = slave(GtRefClkPort(config.gtClock.length, config.gtSingleEnd)).setName("gt")

    val init_clk = !config.initSingleEnd generate in(DiffSignal())
    val init_clk_in = config.initSingleEnd generate in(Bool())
    val init_clk_out = !config.initSingleEnd generate out(Bool())

    /** DRP Ports */
    val drp = slave(DrpPort(config.drpNum))

    /** core control */
    val power_down = in(Bool())
    val loopback = in(Bits(3 bits))
    val pll_not_locked = !config.incore generate in(Bool())

    /** core status */
    val channel_up = out(Bool())
    val frame_err = out(Bool())
    val hard_err = out(Bool())
    val lane_up = out(Bits(config.lanes bits))
    val pll_not_locked_out = config.incore generate out(Bool())
    val tx_resetdone_out = out(Bool())
    val rx_resetdone_out = out(Bool())
    val soft_err = out(Bool())
    val tx_lock = out(Bool())
    val crc_pass_fail_n = config.crc generate out(Bool())
    val crc_valid = config.crc generate out(Bool())

    /** reset */
    val reset = in(Bool())
    val gt_reset = in(Bool())
    val gt_reset_out = config.incore generate out(Bool())

    /** System Interface */
    val link_reset_out = out(Bool())
    val user_clk_out = config.incore generate out(Bool())
    val sync_clk_out = config.incore generate out(Bool())
    val user_clk = !config.incore generate in(Bool())
    val sync_clk = !config.incore generate in(Bool())
    val sys_reset_out = out(Bool())

    /** QPLL control out Ports */
    val gt_qpll = slave(QPLLPort(config.incore, config.laneLoc)).setName("gt")
  }

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()
    var property: String = "set_property -dict [list "
    var properties: List[String] = List()
    val createCmd =
      f"""set aurora8b10bExist [lsearch -exact [get_ips aurora_8b10b_*] aurora_8b10b_${config.name}]
if { $$aurora8b10bExist <0} {
  create_ip -name aurora_8b10b -vendor xilinx.com -library ip -version 11.1 -module_name aurora_8b10b_${config.name}
}"""
    tcls = tcls :+ createCmd

    properties = properties :+ f"CONFIG.C_LANE_WIDTH {${config.laneWidth}}"
    properties = properties :+ f"CONFIG.C_LINE_RATE {${config.lineRate}}"
    properties = properties :+ f"CONFIG.C_REFCLK_FREQUENCY {${config.gtRefClk}}"
    properties = properties :+ f"CONFIG.C_INIT_CLK {${config.initClk}}"
    properties = properties :+ f"CONFIG.DRP_FREQ {${config.drpClk}}"
    properties = properties :+ f"CONFIG.Dataflow_Config {${config.dataFlow}}"
    properties = properties :+ f"CONFIG.Interface_Mode {${config.interface}}"
    properties = properties :+ f"CONFIG.Flow_Mode {${config.flowCtrl}}"
    properties = properties :+ f"CONFIG.SupportLevel {${config.incore.toInt}}"
    if (config.incore) {
      properties = properties :+ f"CONFIG.SINGLEEND_INITCLK {${config.initClkSingleEnd}}"
      properties = properties :+ f"CONFIG.SINGLEEND_GTREFCLK {${config.gtRefClkSingleEnd}}"
    }
    properties = properties :+ f"CONFIG.C_USE_SCRAMBLER {${config.scramber}}"
    properties = properties :+ f"CONFIG.C_USE_CRC {${config.crc}}"
    properties = properties :+ f"CONFIG.C_USE_BYTESWAP {${config.littleEndian}}"
    properties = properties :+ f"CONFIG.C_AURORA_LANES {${config.lanes}}"

    for (i <- 0 until config.gtClock.length)
      properties = properties :+ f"CONFIG.C_GT_CLOCK_${i + 1} {${config.gtClock(i)}}"

    var locSet = Set(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
    for (i <- 0 until config.laneLoc.length) {
      properties =
        properties :+ f"CONFIG.C_GT_LOC_${config.laneLoc(i).postion} {${config.laneLoc(i).setValue}}"
      locSet -= config.laneLoc(i).postion
    }
    locSet.foreach(f => properties = properties :+ f"CONFIG.C_GT_LOC_${f} {X}")

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips aurora_8b10b_${config.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }

  noIoPrefix()
  Axi4SpecRenamer(io.s_axi_tx)
  if (io.s_axi_nfc_tx != null)
    StreamRenamer(io.s_axi_nfc_tx)
  if (io.s_axi_ufc_tx != null)
    StreamRenamer(io.s_axi_ufc_tx)
  DiffSignalRenamer(io.rx)
  DiffSignalRenamer(io.tx)
  Axi4FlowRenamer(io.m_axi_rx)
  if (io.m_axi_nfc_rx != null)
    Axi4FlowRenamer(io.m_axi_nfc_rx)
  if (io.m_axi_ufc_rx != null)
    Axi4FlowRenamer(io.m_axi_ufc_rx)
  io.gt_qpll.specRenamer
  io.drp.specRenamer
  io.gt_refclk.specRenamer

}
