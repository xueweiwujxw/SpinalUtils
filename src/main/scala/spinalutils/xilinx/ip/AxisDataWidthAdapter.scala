package spinalutils.xilinx.ip

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axis._
import spinal.lib.bus.amba4.axi.Axi4SpecRenamer

case class AxisDataWidthAdapterConfig(
    name: String,
    slaveBytes: Int,
    masterBytes: Int,
    useKeep: Boolean = false,
    useStrb: Boolean = false,
    useLast: Boolean = false,
    tidWidth: Int = 0,
    tdestWidth: Int = 0,
    tuserWidth: Int = 0,
    useAclkEn: Boolean = false
) {
  require(slaveBytes >= 1 && slaveBytes <= 512)
  require(masterBytes >= 1 && masterBytes <= 512)
  require(tidWidth >= 0 && tidWidth <= 32)
  require(tdestWidth >= 0 && tdestWidth <= 32)
  require(tuserWidth >= 0 && tuserWidth <= 2048)
}

class AxisDataWidthAdapter(conf: AxisDataWidthAdapterConfig) extends BlackBox with IXilinxIP {

  setDefinitionName(f"axis_dwidth_converter_${conf.name}")

  val axisSlaveConf = Axi4StreamConfig(
    dataWidth = conf.slaveBytes,
    useKeep = conf.useKeep,
    useStrb = conf.useStrb,
    useLast = conf.useLast,
    idWidth = if (conf.tidWidth > 0) conf.tidWidth else -1,
    destWidth = if (conf.tdestWidth > 0) conf.tidWidth else -1,
    userWidth = if (conf.tuserWidth > 0) conf.tidWidth else -1,
    useId = conf.tidWidth > 0,
    useDest = conf.tdestWidth > 0,
    useUser = conf.tuserWidth > 0
  )
  val axisMasterConf = axisSlaveConf.copy(dataWidth = conf.masterBytes)

  val io = new Bundle {
    val aclk = in(Bool())
    val aresetn = in(Bool())

    val s_axis = slave(Axi4Stream(axisSlaveConf))
    val m_axis = master(Axi4Stream(axisMasterConf))

    val aclken = conf.useAclkEn generate in(Bool())
  }

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()

    val createCmd =
      f"""set axisDwidthConvExist [lsearch -exact [get_ips axis_dwidth_converter_*] axis_dwidth_converter_${conf.name}]
if { $$axisDwidthConvExist <0} {
  create_ip -name axis_dwidth_converter -vendor xilinx.com -library ip -version 1.1 -module_name axis_dwidth_converter_${conf.name}
}"""

    tcls = tcls :+ createCmd

    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    properties = properties :+ f"CONFIG.S_TDATA_NUM_BYTES ${conf.slaveBytes}"
    properties = properties :+ f"CONFIG.M_TDATA_NUM_BYTES ${conf.masterBytes}"
    properties = properties :+ f"CONFIG.HAS_TSTRB ${conf.useStrb}"
    properties = properties :+ f"CONFIG.HAS_TKEEP ${conf.useKeep}"
    properties = properties :+ f"CONFIG.HAS_TLAST ${conf.useLast}"
    properties = properties :+ f"CONFIG.TID_WIDTH ${conf.tidWidth}"
    properties = properties :+ f"CONFIG.TDEST_WIDTH ${conf.tdestWidth}"
    properties = properties :+ f"CONFIG.TUSER_BITS_PER_BYTE ${conf.tuserWidth}"
    properties = properties :+ f"CONFIG.HAS_MI_TKEEP ${(conf.slaveBytes < conf.masterBytes).toInt}"

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips axis_dwidth_converter_${conf.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }

  Axi4SpecRenamer(io.m_axis)
  Axi4SpecRenamer(io.s_axis)
  noIoPrefix()

  mapCurrentClockDomain(io.aclk, io.aresetn)
}

object AxisDataWidthAdapter {
  def apply(in: Axi4Stream.Axi4Stream, out: Axi4Stream.Axi4Stream, name: String): AxisDataWidthAdapter = {
    val streamWidthAdapter = new AxisDataWidthAdapter(
      conf = AxisDataWidthAdapterConfig(
        name = name,
        slaveBytes = in.config.dataWidth,
        masterBytes = out.config.dataWidth,
        useKeep = in.config.useKeep,
        useStrb = in.config.useStrb,
        useLast = in.config.useLast,
        tidWidth = if (in.config.idWidth > 0) in.config.idWidth else 0,
        tdestWidth = if (in.config.destWidth > 0) in.config.destWidth else 0,
        tuserWidth = if (in.config.userWidth > 0) in.config.userWidth else 0
      )
    )

    streamWidthAdapter.io.s_axis << in
    streamWidthAdapter.io.m_axis >> out

    streamWidthAdapter
  }
}
