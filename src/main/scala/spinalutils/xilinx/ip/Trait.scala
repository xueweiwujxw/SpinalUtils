package spinalutils.xilinx.ip

trait IXilinxIP {
  def generateTcl(): List[String]
  def generateXdc(): List[String]
  def displayInfo(): Unit = { /* default do nothing */ }
}
