package com.zhang.transport

import java.nio.charset.Charset
import java.util.concurrent.ThreadFactory

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel

import scala.concurrent.{CancellationException, Future, Promise}
import scala.util.Try

/**
  * connect the redis server
  * Created by ZhangJiangke on 2018-06-15 09:39
  */
object NettyTransport {

  lazy val OS_NAME = System.getProperty("os.name", "")

  println(OS_NAME)

  lazy val isUseEpoll = OS_NAME match {
    case _ if (OS_NAME.toLowerCase.indexOf("linux") >= 0 && Epoll.isAvailable()) => true
    case _ => false
  }

  def createEventLoopGroup(threadNum: Int, threadFactory: ThreadFactory): EventLoopGroup = {
    val eventLoopGroup = isUseEpoll match {
      case true =>
        new EpollEventLoopGroup(threadNum, threadFactory)
      case false => new NioEventLoopGroup(threadNum, threadFactory)
    }
    eventLoopGroup
  }

  lazy val bootstrap = initBootstrap()

  /**
    * this just a test. change it later.
    */
  def initBootstrap(): Bootstrap = {

    import java.lang.{Boolean => JBoolean}
    val bootstrap = new Bootstrap()
    val eventLoopGroup = createEventLoopGroup(1, namedThreadFactory(1, "ThreadTestRedis", false))
    bootstrap.group(eventLoopGroup)
    isUseEpoll match {
      case true => bootstrap.channel(classOf[EpollSocketChannel])
      case false => bootstrap.channel(classOf[NioSocketChannel])
    }
    bootstrap
      .option[JBoolean](ChannelOption.SO_REUSEADDR, true)
      .option[JBoolean](ChannelOption.SO_KEEPALIVE, true)
      .option[Integer](ChannelOption.SO_SNDBUF, 256000)
      .option[Integer](ChannelOption.SO_RCVBUF, 256000)
      .handler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          val pipeline = ch.pipeline()
          pipeline.addLast("handler", new RedisHandler)
        }
      })
    bootstrap
  }

  /**
    * create a named thread factory.
    *
    * @param threadNum
    * @param threadName
    * @param daemon
    * @return
    */
  def namedThreadFactory(threadNum: Int, threadName: String, daemon: Boolean): ThreadFactory = {
    val threadFactory = new ThreadFactory {

      import java.util.concurrent.atomic.AtomicInteger

      val threadIndex = new AtomicInteger(0)

      override def newThread(r: Runnable): Thread = {
        val thread = new Thread(r, s"${threadName}_${threadNum}_${threadIndex.getAndIncrement()}")
        thread.setDaemon(daemon)
        thread
      }
    }
    threadFactory
  }

  def channelFuture2ScalaFuture(channelFuture: ChannelFuture): Future[Channel] = {
    val p = Promise[Channel]()
    channelFuture.addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = p complete Try {
        if (future.isSuccess) future.channel()
        else if (future.isCancelled) throw new CancellationException
        else throw future.cause()
      }
    })
    p.future
  }

}

class NettyTransport {

  def startConnect(host: String, port: Int) :Future[Channel] = {
    import  NettyTransport._
    val channelFuture = bootstrap.connect(host, port)
    val future = channelFuture2ScalaFuture(channelFuture)
    future
  }

}

class RedisHandler extends ChannelInboundHandlerAdapter{
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"======= ${ctx.channel().remoteAddress()}")
    new Thread(()=> {
      Thread.sleep(2000)

      val commandByte = "LLEN".getBytes("UTF-8")
      val keyByte = "mylist".getBytes("UTF-8")

      val byteBuf = Unpooled.buffer()
      byteBuf.writeByte('*')
      byteBuf.writeByte('2')
      byteBuf.writeByte('\r')
      byteBuf.writeByte('\n')
      byteBuf.writeByte('$')
      byteBuf.writeInt(commandByte.length)
      byteBuf.writeByte('\r')
      byteBuf.writeByte('\n')
      byteBuf.writeBytes(commandByte)
      byteBuf.writeByte('\r')
      byteBuf.writeByte('\n')
      byteBuf.writeByte('$')
      byteBuf.writeInt(keyByte.length)
      byteBuf.writeByte('\r')
      byteBuf.writeByte('\n')
      byteBuf.writeBytes(keyByte)
      byteBuf.writeByte('\r')
      byteBuf.writeByte('\n')
      ctx.writeAndFlush(byteBuf).addListener(new ChannelFutureListener {
        override def operationComplete(future: ChannelFuture): Unit = {
          if (future.isSuccess) println("++++++++++++++++++++++++++++")
        }
      })
    }).start()
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
    println("--------------------------------------")
    if (msg.isInstanceOf[ByteBuf]) {
      val byteBuf = msg.asInstanceOf[ByteBuf]
      if (byteBuf.hasArray) {
        val array = byteBuf.array
        val offset = byteBuf.arrayOffset + byteBuf.readerIndex
        val length = byteBuf.readableBytes
        val bytes = new Array[Byte](length)
        val byteString = System.arraycopy(array, offset, bytes, 0, length)
        println(s"++++++++++    ${byteString}")
      } else {
        val length = byteBuf.readableBytes
        val array = new Array[Byte](length)
        byteBuf.getBytes(byteBuf.readerIndex(), array)
        val byteString = new String(array)
        println(s"++++++++++    ${byteString}")
      }
    }
  }
}
