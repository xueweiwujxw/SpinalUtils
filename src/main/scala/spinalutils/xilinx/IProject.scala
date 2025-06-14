package spinalutils.xilinx

import spinal.core._
import spinal.lib._

import java.io.File
import java.io.PrintWriter
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.file._
import spinalutils.xilinx.ip.IXilinxIP

case class ProjectConfig(
    part: String,
    name: String = "vivadoPrj",
    version: String = "2021.1"
)

trait IProject {
  var config: ProjectConfig = ProjectConfig(part = "xc7k325tffg900-2")
  def configProject(cfg: ProjectConfig) {
    config = cfg
  }

  def traverseTcl(com: Component): List[String] = {
    var tcls: List[String] = List()
    if (com.isInstanceOf[IXilinxIP]) {
      var ip = com.asInstanceOf[IXilinxIP]
      tcls = tcls ::: ip.generateTcl()
      ip.displayInfo() 
    } else if (com.isInstanceOf[Component]) {
      com.children.map { case c =>
        tcls = tcls ::: traverseTcl(c)
      }
    }
    tcls
  }

  def traverseXdc(com: Component): List[String] = {
    var tcls: List[String] = List()
    if (com.isInstanceOf[IXilinxIP]) {
      tcls = tcls ::: com.asInstanceOf[IXilinxIP].generateXdc()
    } else if (com.isInstanceOf[Component]) {
      com.children.map { case c =>
        tcls = tcls ::: traverseXdc(c)
      }
    }
    tcls
  }

  def outputTcl(filename: String = "generate.tcl") {
    var tcls: List[String] = traverseTcl(this.asInstanceOf[Component])
    val file = new File(filename)
    val writer = new PrintWriter(file)
    println("")
    writer.write("#Create IPs \r\n")
    tcls.zipWithIndex.map { case (t, i) => writer.write(t + "\r\n") }
    writer.close()
  }

  def outputXdc(basefile: String = null, filename: String = "constraints.xdc") {
    val file = new File(filename)
    if (file.exists()) {
      file.delete()
    }

    if (basefile != null) {
      val base = new File(basefile)
      if (base.exists()) {
        Files.copy(
          base.toPath(),
          file.toPath(),
          StandardCopyOption.REPLACE_EXISTING
        )
      }
    }

    val fw = new FileWriter(file, true)
    val bw = new BufferedWriter(fw)
    val pw = new PrintWriter(bw)
    var xdcs: List[String] = traverseXdc(this.asInstanceOf[Component])
    xdcs.zipWithIndex.map { case (x, i) => pw.println(x) }
    pw.close()
    bw.close()
    fw.close()
  }
}
