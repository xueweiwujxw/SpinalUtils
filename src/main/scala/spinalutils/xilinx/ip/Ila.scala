package spinalutils.xilinx.ip

import spinal.core._
import spinal.lib._

object IlaTriggerOptions extends SpinalEnum(binarySequential) {
  val DATA_AND_TRIGGER, DATA, TRIGGER = newElement()
}

case class IlaConfig(
    name: String,
    probes: List[Int],
    depth: Int,
    captureControl: Boolean = true,
    advancedTrigger: Boolean = true,
    sameComparators: Boolean = true,
    comparator: Int = 2,
    comparators: List[Int] = null,
    triggerOptions: List[IlaTriggerOptions.E] = null
) {
  if (!sameComparators)
    require(comparators != null && comparators.length == probes.length)
  if (triggerOptions != null)
    require(triggerOptions.length == probes.length)
}

case class Ila(conf: IlaConfig) extends BlackBox with IXilinxIP {
  setDefinitionName(f"ila_${conf.name}")

  val io = new Bundle {
    val clk = in(Bool())
    val probe = conf.probes.zipWithIndex.map { case (w, i) =>
      val ret = in(Bits(w bits))
      ret.setName(f"probe${i}")
      ret
    }
  }

  mapCurrentClockDomain(io.clk)
  noIoPrefix()

  override def displayInfo(): Unit = {
    println(f"name:ila_${conf.name}, width: ${conf.probes}")
  }

  def generateTcl(): List[String] = {
    var tcls: List[String] = List()

    val createCmd = f"""set ilaExit [lsearch -exact [get_ips ila*] ila_${conf.name}]
if { $$ilaExit <0 } {
  create_ip -name ila -vendor xilinx.com -library ip -module_name ila_${conf.name}
}"""
    tcls = tcls :+ createCmd

    var property: String = "set_property -dict [list "
    var properties: List[String] = List()

    properties = properties :+ f"CONFIG.C_DATA_DEPTH {${conf.depth}}"
    conf.probes.zipWithIndex.map { case (w, i) =>
      properties = properties :+ f"CONFIG.C_PROBE${i}_WIDTH {${w}}"
    }
    properties = properties :+ f"CONFIG.C_NUM_OF_PROBES {${conf.probes.length}}"

    properties = properties :+ f"CONFIG.ALL_PROBE_SAME_MU {${conf.sameComparators}}"
    if (conf.sameComparators) {
      properties = properties :+ f"CONFIG.ALL_PROBE_SAME_MU_CNT {${conf.comparator}}"
    } else {
      conf.comparators.zipWithIndex.map { case (c, i) =>
        properties = properties :+ f"CONFIG.C_PROBE${i}_MU_CNT {${c}}"
      }
    }

    if (conf.triggerOptions != null) {
      conf.triggerOptions.zipWithIndex.map { case (t, i) =>
        properties = properties :+ f"CONFIG.C_PROBE${i}_TYPE {${t.position}}"
      }
    }

    properties = properties :+ f"CONFIG.C_ADV_TRIGGER {${conf.advancedTrigger}}"
    properties = properties :+ f"CONFIG.C_EN_STRG_QUAL {${conf.captureControl.toInt}}"

    properties.foreach { x =>
      if (x != "")
        property = property.concat(x) + " "
    }
    property = property.concat(f"] [get_ips ila_${conf.name}]")

    tcls :+ property
  }

  def generateXdc(): List[String] = {
    var xdcs: List[String] = List()

    xdcs
  }
}

object Ila {
  def apply[T <: Data](name: String, depth: Int, signals: T*) = {
    val signalConvert = signals.flatMap(s =>
      s match {
        case x: Vec[_] => x.toList // 仅在一维数组下正常工作
        case x: Bundle => x.flatten.toList
        case _         => List(s)
      }
    )
    val signalWidths = signalConvert.map(B(_).getWidth).toList
    val ila = new Ila(
      IlaConfig(
        name = name,
        probes = signalWidths,
        depth = 1024
      )
    )
    ila.setName(f"${name}_ila")

    ila.io.probe.zip(signalConvert).foreach { case (probei, s) =>
      probei := (s.asBits).setName(s.getName(), true)
    }

    ila
  }

  def apply[T <: Data](name: String, depth: Int, signals: List[T]) = {
    val signalWidths = signals.map(B(_).getWidth).toList
    val ila = new Ila(
      IlaConfig(
        name = name,
        probes = signalWidths,
        depth = 1024
      )
    )
    ila.setName(f"${name}_ila")

    ila.io.probe.zip(signals).foreach { case (probei, s) =>
      probei := (s.asBits).setName(s.getName(), true)
    }

    ila
  }
}
