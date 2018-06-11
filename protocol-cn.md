
# Redis 协议定义
Redis客户端使用一个称做**RESP**（REdis Serialization Protocol）的序列化协议与Redis服务器交互。虽然这个协议专为Redis设计，但是它可以应用到其它的client-server软件项目中。
RESP是妥协以下事情间的产物：
* 实现简单
* 快速解析
* 可读