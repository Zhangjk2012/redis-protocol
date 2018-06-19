package com.zhang.transport

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * test the netty connected redis.
  * Created by ZhangJiangke on 2018-06-15 10:25
  */
class NettyTransportSpec extends FlatSpec with Matchers with ScalaFutures {

  "simple test the netty client connect the Redis Server" should " success" in {
    val nettyTransport = new NettyTransport()
    val future = nettyTransport.startConnect("localhost", 6379)
    import scala.concurrent.ExecutionContext.Implicits.global
    future.foreach(channel => {
      channel.isActive shouldBe true
    })
    Await.result(future, 10 seconds)
  }

}
