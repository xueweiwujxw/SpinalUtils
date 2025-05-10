package spinalutils.xilinx.ip

import spinal.core._

object ClkWizPrimitiveOptions extends SpinalEnum(binarySequential) {
  val MMCM, PLL = newElement()
  MMCM.setName("MMCM")
  PLL.setName("PLL")
}

object ClkWizResetOptions extends SpinalEnum(binarySequential) {
  val LOW, HIGH = newElement()
  LOW.setName("ACTIVE_LOW")
  HIGH.setName("ACTIVE_HIGH")
}

object ClkWizDrivesOptions extends SpinalEnum(binarySequential) {
  val BUFG, BUFH, BUFGCE, BUFHCE, NO_BUFFER = newElement()
  BUFG.setName("BUFG")
  BUFH.setName("BUFH")
  BUFGCE.setName("BUFGCE")
  BUFHCE.setName("BUFHCE")
  NO_BUFFER.setName("No_buffer")
}

object ClkWizJitterOptions extends SpinalEnum(binarySequential) {
  val UI, PS = newElement()
  UI.setName("UI")
  PS.setName("PS")
}

case class ClkWizConfig(
    name: String,
    freqIn: Double,
    freqOut: List[Double],
    freqDrives: List[ClkWizDrivesOptions.E] = null,
    primitive: ClkWizPrimitiveOptions.E = ClkWizPrimitiveOptions.MMCM,
    jitterOpt: ClkWizJitterOptions.E = ClkWizJitterOptions.UI,
    inputJitter: Double = 0.010,
    dualIn: Boolean = false,
    reset: Boolean = false,
    locked: Boolean = true,
    resetType: ClkWizResetOptions.E = ClkWizResetOptions.HIGH
) {
  if (freqDrives != null)
    require(freqOut.length == freqDrives.length)
  val jitterPs =
    if (jitterOpt == ClkWizJitterOptions.UI) inputJitter * (1000 / freqIn) * 1000 else inputJitter
  require(jitterPs >= 10 && jitterPs <= 993, f"jitterPs ${jitterPs} should be between 10 with 993")
}

case class ClkWiz(conf: ClkWizConfig) extends BlackBox with IXilinxIP {
  setDefinitionName(f"clk_wiz_${conf.name}")

  val io = new Bundle {
    val clk_in1 = !conf.dualIn generate in(Bool())
    val clk_in1_p = conf.dualIn generate in(Bool())
    val clk_in1_n = conf.dualIn generate in(Bool())
    val clk_out = conf.freqOut.zipWithIndex.map { case (f, i) =>
      val clk_outn = out(Bool())
      clk_outn.setName(f"clk_out${i + 1}")
      clk_outn
    }
    val locked = conf.locked generate out(Bool())
    val reset = conf.reset generate out(Bool())

    if (conf.reset) {
      reset.setName(if (conf.resetType == ClkWizResetOptions.LOW) "resetn" else "reset")
    }
  }

  noIoPrefix()

  override def displayInfo(): Unit = {
    println(
      f"name:clk_wiz_${conf.name}, inFreq: ${conf.freqIn}, outFreq: ${conf.freqOut}, jitterPs: ${conf.jitterPs}"
    )
  }

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()

    val createCmd = f"""set clkWizExit [lsearch -exact [get_ips clk_wiz_*] clk_wiz_${conf.name}]
if { $$clkWizExit <0} {
  create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 -module_name clk_wiz_${conf.name}
}"""
    tcls = tcls :+ createCmd

    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    properties = properties :+ f"CONFIG.PRIMITIVE {${conf.primitive.getName()}}"
    properties = properties :+ f"CONFIG.PRIM_IN_FREQ {${conf.freqIn}}"
    properties = properties :+ f"CONFIG.PRIM_SOURCE {${if (conf.dualIn) "Differential_clock_capable_pin"
      else "Single_ended_clock_capable_pin"}}"
    if (conf.freqOut.length == 1)
      properties = properties :+ f"CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {${conf.freqOut(0)}}"
    else
      conf.freqOut.zipWithIndex.map { case (f, i) =>
        properties = properties :+ f"CONFIG.CLKOUT${i + 1}_USED {true} CONFIG.CLKOUT${i + 1}_REQUESTED_OUT_FREQ ${f}"
      }
    if (conf.freqDrives != null)
      conf.freqDrives.zipWithIndex.map { case (d, i) =>
        properties = properties :+ f"CONFIG.CLKOUT${i + 1}_DRIVES {${d.getName()}}"
      }
    properties = properties :+ f"CONFIG.JITTER_OPTIONS {${conf.jitterOpt.getName()}}"
    if (conf.jitterOpt == ClkWizJitterOptions.UI)
      properties = properties :+ f"CONFIG.CLKIN1_UI_JITTER {${conf.inputJitter}}"
    else
      properties = properties :+ f"CONFIG.CLKIN1_JITTER_PS {${conf.jitterPs}}"
    for (i <- conf.freqOut.length + 1 to 7)
      properties = properties :+ f"CONFIG.CLKOUT${i}_USED {false}"
    properties = properties :+ f"CONFIG.USE_LOCKED {${conf.locked}}"
    properties = properties :+ f"CONFIG.USE_RESET {${conf.reset}}"
    if (conf.reset) {
      properties = properties :+ f"CONFIG.RESET_PORT {${if (conf.resetType == ClkWizResetOptions.LOW) "resetn"
        else "reset"}}"
    }

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips clk_wiz_${conf.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }

}

object ClkWiz {
  def adjustJitter(freq: Double, startJitter: Double = 0.01): Double = {
    val ps = 1000 / freq * 1000
    var jitterUi = startJitter
    while (jitterUi * ps > 993)
      jitterUi /= 10
    while (jitterUi * ps < 10)
      jitterUi *= 10

    jitterUi
  }

  def apply(
      name: String,
      freqIn: Double,
      freqOut: List[Double],
      dualIn: Boolean = false
  ): ClkWiz = {
    val ret = new ClkWiz(
      ClkWizConfig(
        name = name,
        freqIn = freqIn,
        freqOut = freqOut,
        dualIn = dualIn,
        inputJitter = adjustJitter(freq = freqIn)
      )
    )
    ret
  }
  def apply(name: String, freqIn: Double, freqOut: List[Double]): ClkWiz = {
    val ret = new ClkWiz(
      ClkWizConfig(
        name = name,
        freqIn = freqIn,
        freqOut = freqOut,
        dualIn = false,
        inputJitter = adjustJitter(freq = freqIn)
      )
    )
    ret
  }
  def apply(
      name: String,
      freqIn: Double,
      freqOut: Double,
      dualIn: Boolean
  ): ClkWiz = {
    val ret = new ClkWiz(
      ClkWizConfig(
        name = name,
        freqIn = freqIn,
        freqOut = List(freqOut),
        dualIn = dualIn,
        inputJitter = adjustJitter(freq = freqIn)
      )
    )
    ret
  }
}
