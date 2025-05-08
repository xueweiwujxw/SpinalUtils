package spinalutils.xilinx.ip

import spinal.core._

import java.io._

object clk_wiz {
  def apply(
      wizname: String,
      freqin: Double,
      freqout: List[Double],
      dual: Boolean = false
  ): clk_wiz = {
    val ret = new clk_wiz(wizname, freqin, freqout, dual)
    ret
  }
  def apply(wizname: String, freqin: Int, freqout: Double): clk_wiz = {
    val ret = new clk_wiz(wizname, freqin, List(freqout), false)
    ret
  }
  def apply(
      wizname: String,
      freqin: Double,
      freqout: Double,
      dual: Boolean
  ): clk_wiz = {
    val ret = new clk_wiz(wizname, freqin, List(freqout), dual)
    ret
  }
}

class clk_wiz(
    wizname: String,
    freqin: Double,
    freqout: List[Double],
    dual: Boolean = false
) extends BlackBox {
  val io = new Bundle {
    val clk_in1 = if (dual) null else in Bool ()
    val clk_in1_n = if (dual) in Bool () else null
    val clk_in1_p = if (dual) in Bool () else null
    val clk_out = freqout.zipWithIndex.map { case (f, idx) =>
      val ret = out Bool ()
      ret.setName(f"clk_out${idx + 1}")
      ret
    }
    val locked = out(Bool())
  }

  val dualString =
    if (dual) f" CONFIG.PRIM_SOURCE {Differential_clock_capable_pin} "
    else f" CONFIG.PRIM_SOURCE {Single_ended_clock_capable_pin} "

  val clkcmd = if (freqout.length == 1) {
    f"CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {${freqout(0)}} "
  } else {
    freqout.zipWithIndex
      .map { case (freq, idx) =>
        f"CONFIG.CLKOUT${idx + 1}_USED {true} CONFIG.CLKOUT${idx + 1}_REQUESTED_OUT_FREQ {${freq}} "
      }
      .reduce(_ + _)
  }

  val createCmd = f"""set wizExit [lsearch -exact [get_ips clk_wiz*] clk_wiz_${wizname}]
if { $$wizExit <0} {
  create_ip -name clk_wiz -vendor xilinx.com -library ip  -module_name clk_wiz_${wizname}
}
set_property -dict [list${dualString}CONFIG.PRIM_IN_FREQ {${freqin}} ${clkcmd}CONFIG.USE_LOCKED {true} CONFIG.USE_RESET {false}] [get_ips clk_wiz_${wizname}]\n\n"""

  this.setDefinitionName("clk_wiz_" + wizname)
  PrintTcl(createCmd)

  noIoPrefix()

}
