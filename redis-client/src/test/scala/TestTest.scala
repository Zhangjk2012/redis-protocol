import com.zhang.transport.NettyTransport
import io.netty.buffer.Unpooled

/**
  *
  * Created by ZhangJiangke on 2018-06-11 19:54
  */
object TestTest extends App {
//  val nettyTransport = new NettyTransport()
//  val future = nettyTransport.startConnect("localhost", 6379)

//  println('*'.toByte)
//  println("LLEN".getBytes("UTF-8").length)
//  println('$'.toByte)

  private val digits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')


  intToByte(23456)


  def  intToByte(value: Int) = {
    var t = value
    var flag = true
    var q, r = 0
    while (flag) {
      q = (t * 52429) >>> (16 + 3)
      r = t - ((q << 3) + (q << 1))
      println( "====" + r)
      val s = digits(r)
      println(digits(r))
      t = q
      if (t == 0) flag = false
    }
  }
}
