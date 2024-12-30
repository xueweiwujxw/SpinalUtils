package spinalutils.xilinx.ip

import java.io._

object PrintTcl {
  private var tcl: List[String] = List()
  private var tclsyn: List[String] = List()

  def apply(input: String) {
    tcl = tcl :+ input
    tclsyn = tclsyn :+ input
  }
  def apply(address: String, FileName: String) {
    val file = new File(address)
    if (!file.exists())
      file.mkdir()

    val writer = new PrintWriter(
      new File(address + System.getProperty("file.separator") + FileName)
    )
    tcl.map(x => writer.write(x))
    writer.close
  }
  def apply(input: String, synth: Int) {
    tclsyn = tclsyn :+ input
  }
  def clear() = { tcl = List(); tclsyn = List() }
  def get(): String = {
    val ret = tclsyn.reduce(_ + _)
    ret
  }
}
