package spinalutils.xilinx.ip

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axilite._
import spinal.lib.bus.amba4.axis._
import spinal.lib.bus.amba4.axi._

object XadcInterfaceOptions extends SpinalEnum(binarySequential) {
  val AXI4Lite, DRP, None = newElement()
  AXI4Lite.setName("Enable_AXI")
  DRP.setName("ENABLE_DRP")
  None.setName("None")
}

object XadcTimingModes extends SpinalEnum(binarySequential) {
  val Continuous, Event = newElement()
  Continuous.setName("Continuous")
  Event.setName("Event")
}

case class Drp() extends Bundle with IMasterSlave with IConnectable[Drp] {
  val daddr_in = Bits(7 bits)
  val den_in = Bool()
  val di_in = Bits(16 bits)
  val do_out = Bits(16 bits)
  val drdy_out = Bool()
  val dwe_in = Bool()

  def asMaster(): Unit = {
    out(daddr_in, den_in, di_in, dwe_in)
    in(do_out, drdy_out)
  }

  def connectFrom(that: Drp): Drp = {
    this.daddr_in := that.daddr_in
    this.den_in := that.den_in
    this.di_in := that.di_in
    this.dwe_in := that.dwe_in
    that.do_out := this.do_out
    that.drdy_out := this.drdy_out

    that
  }
}

case class XadcAlarmsConfig(
    useOverTemperature: Boolean = true,
    useUserTemperature: Boolean = true,
    useVCCINT: Boolean = true,
    useVCCAUX: Boolean = true,
    useVCCBRAM: Boolean = true,
    OverTemperatureTrigger: Double = 125,
    OverTemperatureReset: Double = 70,
    UserTemperatureTrigger: Double = 85,
    UserTemperatureReset: Double = 60,
    VCCINTLower: Double = 0.97,
    VCCINTUpper: Double = 1.03,
    VCCAUXLower: Double = 1.75,
    VCCAUXUpper: Double = 1.89,
    VCCBRAMLower: Double = 0.95,
    VCCBRAMUpper: Double = 1.05
) {
  require(
    OverTemperatureTrigger >= -40 && OverTemperatureTrigger <= 125,
    "OverTemperatureTrigger: [-40, 125]"
  )
  require(
    OverTemperatureReset >= -40 && OverTemperatureReset <= 125,
    "OverTemperatureReset: [-40, 125]"
  )
  require(
    UserTemperatureTrigger >= -40 && UserTemperatureTrigger <= 125,
    "UserTemperatureTrigger: [-40, 125]"
  )
  require(
    UserTemperatureReset >= -40 && UserTemperatureReset <= 125,
    "UserTemperatureReset: [-40, 125]"
  )
  require(VCCINTLower >= 0 && VCCINTLower <= 1.05, "VCCINTLower: [0, 1.05]")
  require(VCCINTUpper >= 0 && VCCINTUpper <= 1.05, "VCCINTUpper: [0, 1.05]")
  require(VCCAUXLower >= 0 && VCCAUXLower <= 1.89, "VCCAUXLower: [0, 1.89]")
  require(VCCAUXUpper >= 0 && VCCAUXUpper <= 1.89, "VCCAUXUpper: [0, 1.89]")
  require(VCCBRAMLower >= 0 && VCCBRAMLower <= 1.05, "VCCBRAMLower: [0, 1.05]")
  require(VCCBRAMUpper >= 0 && VCCBRAMUpper <= 1.05, "VCCBRAMUpper: [0, 1.05]")
}

case class XadcConfig(
    name: String,
    drpFreq: Double,
    interface: XadcInterfaceOptions.E = XadcInterfaceOptions.DRP,
    timing: XadcTimingModes.E = XadcTimingModes.Continuous,
    streamEn: Boolean = false,
    streamFIFODepth: Int = 7,
    alarms: XadcAlarmsConfig = XadcAlarmsConfig()
)

case class Xadc(conf: XadcConfig) extends BlackBox with IXilinxIP {

  setDefinitionName(f"xadc_wiz_${conf.name}")

  val io = new Bundle {
    val s_drp = conf.interface == XadcInterfaceOptions.DRP generate slave(Drp()).setName("")
    val vn_in = in(Bool())
    val vp_in = in(Bool())

    val dclk_in =
      conf.interface != XadcInterfaceOptions.AXI4Lite && !conf.streamEn generate in(Bool())
    val reset_in =
      conf.interface != XadcInterfaceOptions.AXI4Lite && !conf.streamEn generate in(Bool())

    val s_axi = conf.interface == XadcInterfaceOptions.AXI4Lite generate slave(
      AxiLite4(
        AxiLite4Config(
          addressWidth = 11,
          dataWidth = 32
        )
      )
    )

    val s_axi_aclk = conf.interface == XadcInterfaceOptions.AXI4Lite generate in(Bool())
    val s_axi_aresetn = conf.interface == XadcInterfaceOptions.AXI4Lite generate in(Bool())

    val s_axis_aclk = conf.streamEn generate in(Bool())
    val m_axis_aclk =
      conf.streamEn && conf.interface != XadcInterfaceOptions.AXI4Lite generate in(Bool())
    val m_axis_resetn =
      conf.streamEn && conf.interface != XadcInterfaceOptions.AXI4Lite generate in(Bool())

    val convst_in = conf.timing == XadcTimingModes.Event generate in(Bool())

    val m_axis =
      conf.streamEn generate master(
        Axi4Stream(Axi4StreamConfig(dataWidth = 2, idWidth = 5, useId = true))
      )

    val ip2intc_irpt = conf.interface == XadcInterfaceOptions.AXI4Lite generate out(Bool())

    val ot_out = conf.alarms.useOverTemperature generate out(Bool())
    val user_temp_alarm_out = conf.alarms.useUserTemperature generate out(Bool())
    val vccint_alarm_out = conf.alarms.useVCCINT generate out(Bool())
    val vccaux_alarm_out = conf.alarms.useVCCAUX generate out(Bool())
    val vbram_alarm_out = conf.alarms.useVCCBRAM generate out(Bool())

    val channel_out = out(Bits(5 bits))
    val eoc_out = out(Bool())
    val alarm_out = out(Bool())
    val eos_out = out(Bool())
    val busy_out = out(Bool())
  }

  if (io.m_axis != null)
    Axi4SpecRenamer(io.m_axis)
  if (io.s_axi != null)
    AxiLite4SpecRenamer(io.s_axi)
  noIoPrefix()

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()

    val createCmd = f"""set xadcWizExist [lsearch -exact [get_ips xadc_wiz_*] xadc_wiz_${conf.name}]
if { $$xadcWizExist <0} {
  create_ip -name xadc_wiz -vendor xilinx.com -library ip -version 3.3 -module_name xadc_wiz_${conf.name}
}"""
    tcls = tcls :+ createCmd

    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    properties = properties :+ f"CONFIG.INTERFACE_SELECTION {${conf.interface.getName()}}"
    properties = properties :+ f"CONFIG.TIMING_MODE {${conf.timing.getName()}}"
    properties = properties :+ f"CONFIG.DCLK_FREQUENCY {${conf.drpFreq}}"
    properties = properties :+ f"CONFIG.ENABLE_RESET {${conf.interface != XadcInterfaceOptions.AXI4Lite}}"
    properties = properties :+ f"CONFIG.ENABLE_AXI4STREAM {${conf.streamEn}}"
    properties = properties :+ f"CONFIG.FIFO_DEPTH {${conf.streamFIFODepth}}"
    properties = properties :+ f"CONFIG.OT_ALARM {${conf.alarms.useOverTemperature}}"
    properties = properties :+ f"CONFIG.TEMPERATURE_ALARM_OT_TRIGGER {${conf.alarms.OverTemperatureTrigger}}"
    properties = properties :+ f"CONFIG.TEMPERATURE_ALARM_OT_RESET {${conf.alarms.OverTemperatureReset}}"
    properties = properties :+ f"CONFIG.USER_TEMP_ALARM {${conf.alarms.useUserTemperature}}"
    properties = properties :+ f"CONFIG.TEMPERATURE_ALARM_TRIGGER {${conf.alarms.OverTemperatureTrigger}}"
    properties = properties :+ f"CONFIG.TEMPERATURE_ALARM_RESET {${conf.alarms.OverTemperatureReset}}"
    properties = properties :+ f"CONFIG.VCCINT_ALARM {${conf.alarms.useVCCINT}}"
    properties = properties :+ f"CONFIG.VCCINT_ALARM_LOWER {${conf.alarms.VCCINTLower}}"
    properties = properties :+ f"CONFIG.VCCINT_ALARM_UPPER {${conf.alarms.VCCINTUpper}}"
    properties = properties :+ f"CONFIG.VCCAUX_ALARM {${conf.alarms.useVCCAUX}}"
    properties = properties :+ f"CONFIG.VCCAUX_ALARM_LOWER {${conf.alarms.VCCAUXLower}}"
    properties = properties :+ f"CONFIG.VCCAUX_ALARM_UPPER {${conf.alarms.VCCAUXUpper}}"
    properties = properties :+ f"CONFIG.ENABLE_VBRAM_ALARM {${conf.alarms.useVCCBRAM}}"
    properties = properties :+ f"CONFIG.VBRAM_ALARM_LOWER {${conf.alarms.VCCBRAMLower}}"
    properties = properties :+ f"CONFIG.VBRAM_ALARM_UPPER {${conf.alarms.VCCBRAMUpper}}"

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips xadc_wiz_${conf.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }
}
