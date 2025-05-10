package spinalutils.xilinx.ip

import spinal.core._

import java.nio.file.Paths
import java.io._

case class VioConfig(
    name: String,
    outProbes: List[Int],
    outInits: List[BigInt] = null,
    inProbes: List[Int] = List(),
    inProbeActiveDetector: Boolean = true
) {
  if (outInits != null)
    require(outProbes.length == outInits.length)
}

case class Vio(conf: VioConfig) extends BlackBox with IXilinxIP {
  setDefinitionName(f"vio_${conf.name}")

  val io = new Bundle {
    val clk = in Bool ()
    val probe_out = conf.outProbes.zipWithIndex.map { case (w, i) =>
      val ret = out(Bits(w bits))
      ret.setName(f"probe_out${i}")
      ret
    }
    val probe_in = conf.inProbes.length != 0 generate conf.inProbes.zipWithIndex.map { case (w, i) =>
      val ret = in(Bits(w bits))
      ret.setName(f"probe_in${i}")
      ret
    }
  }

  mapCurrentClockDomain(io.clk)
  noIoPrefix()

  override def displayInfo(): Unit = {
    println(
      f"name:vio_${conf.name}, out_width: ${conf.outProbes}${if (conf.inProbes != 0)
          f", in_width: ${conf.inProbes}"
        else ""}"
    )
  }

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()

    val createCmd = f"""set vioExit [lsearch -exact [get_ips vio*] vio_${conf.name}]
if { $$vioExit <0} {
  create_ip -name vio -vendor xilinx.com -library ip -module_name vio_${conf.name}
}"""
    tcls = tcls :+ createCmd

    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    if (conf.inProbes.length != 0)
      properties = properties :+ f"CONFIG.C_EN_PROBE_IN_ACTIVITY {${conf.inProbeActiveDetector.toInt}}"

    properties = properties :+ f"CONFIG.C_NUM_PROBE_IN {${conf.inProbes.length}}"
    conf.inProbes.zipWithIndex.map { case (w, i) =>
      properties = properties :+ f"CONFIG.C_PROBE_IN${i}_WIDTH {${w}}"
    }

    properties = properties :+ f"CONFIG.C_NUM_PROBE_OUT {${conf.outProbes.length}}"
    conf.outProbes.zip(conf.outInits).zipWithIndex.map { case ((w, init), i) =>
      properties = properties :+ f"CONFIG.C_PROBE_OUT${i}_WIDTH {${w}}"
      properties = properties :+ f"CONFIG.C_PROBE_OUT${i}_INIT_VAL {0x${init}%x}"
    }

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips vio_${conf.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }
}

object Vio {
  def apply[T <: Data](name: String, signals: T*) = {
    val signalConvert = signals.flatMap(s => {
      s match {
        case x: Vec[_] => x.toList
        case x: Bundle => x.flatten.toList
        case _         => List(s)
      }
    })

    val signalWidths = signalConvert.map(B(_).getBitsWidth).toList
    val inits = signalWidths.map(_ => BigInt(0))
    val vio = new Vio(VioConfig(name = name, outProbes = signalWidths))
    vio.setName(f"${name}_vio")

    vio.io.probe_out.zip(signalConvert).foreach { case (probeo, s) =>
      s match {
        case bits_v: Bits =>
          bits_v := probeo.asBits
        case bool_v: Bool =>
          bool_v := probeo.asBool
        case sint_v: SInt =>
          sint_v := probeo.asSInt
        case uint_v: UInt =>
          uint_v := probeo.asUInt
        case _ =>
      }
    }

    vio
  }

  def apply[T <: Data](name: String, signalWidths: List[Int], inits: List[_]) = {
    require(signalWidths.length == inits.length, "signalWidths length must match inits length")
    val bigInits = inits match {
      case list: List[_] if list.forall(_.isInstanceOf[BigInt]) =>
        list.asInstanceOf[List[BigInt]]

      case list: List[_] if list.forall(_.isInstanceOf[Int]) =>
        list.asInstanceOf[List[Int]].map(v => BigInt(v))

      case _ =>
        throw new IllegalArgumentException("Inits must be a List of Int or BigInt")
    }

    signalWidths.zip(bigInits).foreach { case (width, init) =>
      require(init.bitLength <= width, f"init value ${init} is out of range of width ${width}")
    }

    val vio = new Vio(VioConfig(name = name, outProbes = signalWidths, outInits = bigInits))
    vio.setName(f"${name}_vio")

    vio
  }
}
