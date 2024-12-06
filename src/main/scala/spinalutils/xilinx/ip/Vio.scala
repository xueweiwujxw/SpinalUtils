package spinalutils.xilinx.ip

import spinal.core._

import java.nio.file.Paths
import java.io._

class vio(vioname: String, probes: List[Int]) extends BlackBox {
  var i = 0
  val io = new Bundle {
    val clk = in Bool ()
    val probe_out = probes.map { x =>
      val ret = out Bits (x bits)
      ret.setName(f"probe_out${i}")
      i = i + 1
      ret
    }
  }

  mapCurrentClockDomain(io.clk)
  noIoPrefix()
  this.setDefinitionName("vio_" + vioname)
  println("name:vio_" + vioname + ",width:" + probes)

}

object vio {
  def outTCL(name: String, probes: List[Int], inits: List[BigInt]) = {
    val tcl = new PrintWriter(new File("vivadoVio_" + name + ".tcl"))

    val createVioCmd = f"""set vioExit [lsearch -exact [get_ips vio*] vio_${name}]
if { $$vioExit <0} {
  create_ip -name vio -vendor xilinx.com -library ip -module_name vio_${name}
}\n"""
    tcl.write(createVioCmd)
    tcl.write("set_property -dict [list")
    PrintTcl(createVioCmd)
    PrintTcl("set_property -dict [list")

    var i = 0
    probes.map { x =>
      tcl.write(" CONFIG.C_PROBE_OUT" + i + "_WIDTH {" + x + "}")
      tcl.write(" CONFIG.C_PROBE_OUT" + i + "_INIT_VAL {" + inits(i).formatted("0x%x") + "}")
      PrintTcl(" CONFIG.C_PROBE_OUT" + i + "_WIDTH {" + x + "}")
      PrintTcl(" CONFIG.C_PROBE_OUT" + i + "_INIT_VAL {" + inits(i).formatted("0x%x") + "}")
      i = i + 1
    }
    tcl.write(
      " CONFIG.C_NUM_PROBE_OUT {" + i + "} CONFIG.C_EN_PROBE_IN_ACTIVITY {0} CONFIG.C_NUM_PROBE_IN {0}] [get_ips vio_" + name + "]\n\n"
    )
    PrintTcl(
      " CONFIG.C_NUM_PROBE_OUT {" + i + "} CONFIG.C_EN_PROBE_IN_ACTIVITY {0} CONFIG.C_NUM_PROBE_IN {0}] [get_ips vio_" + name + "]\n\n"
    )

    PrintTcl(f"synth_ip [get_ips vio_${name}]\n", 1)

    tcl.close()
  }

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
    val vivadoVio = new vio(name, signalWidths)

    vivadoVio.io.probe_out.zip(signalConvert).foreach { case (probeo, s) =>
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

    outTCL(name, signalWidths, inits)

    vivadoVio
  }

  def apply[T <: Data](name: String, signalWidths: List[Int], inits: List[_]): vio = {
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

    val vivadoVio = new vio(name, signalWidths)

    outTCL(name, signalWidths, bigInits)

    vivadoVio
  }
}
