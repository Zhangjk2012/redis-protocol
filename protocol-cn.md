
# Redis 协议定义
Redis客户端使用一个称做**RESP**（REdis Serialization Protocol）的序列化协议与Redis服务器交互。虽然这个协议专为Redis设计，但是它可以应用到
其它的client-server软件项目中。

RESP是妥协以下事情间的产物：

* 实现简单
* 快速解析
* 人可读

RESP可以序列化不同的数据类型（data types），比如：整型（integers）、字符串（strings）、数组（arrays）。当然也有具体的错误类型。客户端发送到
Redis服务端的请求，是一个字符串数组，当做执行命令的参数。Redis使用一个特定命令（command-specific）数据类型作为回复。

RESP是二进制安全的（binary-safe），不要求块数据（bulk data）处理时从一个进程转移到另一个进程，因为它使用前缀长度（prefixed-length）来传递块数据。

**注意：这里的协议概述，仅仅是用来client-server间的交互。Redis Cluster使用不同的二进制协议，为了节点（node）之间的信息交换。**

网络层（Networking layer）
-----------------------

一个客户端创建一个TCP连接，连接到Redis服务器的6379端口。

虽然RESP在技术上不是TCP专用的协议，但是，在Redis中，这个协议仅和TCP连接使用（或者等效的流定向连接，比如Unix套接字）

请求响应模型（Request-Response model）
----------------------------------

Redis接收由不同参数组成的命令。一旦命令被接收，它将会被处理，并且一个回复被发送回客户端。

这可能是最简单的模型，但是它有两个例外（exceptions）：

* Redis支持管道（在文档后面介绍）。所以，客户端可能一次发送多条命令，并等待回应。
* 当一个Redis客户端订阅了一个Pub/Sub频道（channel），协议改变了语义，并变成一个 *push* 协议，换言之，客户端不再要求发送（send）命令，因为服务
器会自动给客户端发送新的消息，（订阅频道的客户端）将会很快接收到消息。

不包括以上两个特例，Redis的协议是简单的请求-响应（request-response）协议。

RESP协议描述（RESP protocol description）
--------------------------------------

RESP协议在Redis 1.2版本时引入，但是它是从Redis 2.0版本才成为与服务器交互的标准协议。你应该在你的Redis客户端实现这个协议。

RESP实际上是一个序列化协议，并支持一下数据类型：简单字符串（Simple Strings），错误（Errors），整型（Integers），块字符串（Bulk Strings）和
数组（Arrays）。

RESP作为请求-响应（request-response）协议在Redis中使用的方式如下:

* 客户端发送一个将命令作为块字符串的RESP数组到Redis服务器。
* 服务器按照命令（command）实现，使用RESP类型之一回复。

在RESP中，一些数据类型依赖于它的第一个字节：

* **Simple Strings** 回复的第一个字节是 "+"
* **Errors** 回复的第一个字节是 "-"
* **Integers** 回复的第一个字节是 ":"
* **Bulk Strings** 回复的第一个字节是 "$"
* **Arrays** 回复的第一个字节是 "`*`"

另外，RESP能够使用稍后指定的一个块字符串（Bulk Strings）或者数组（Array）的特殊变量来表示一个Null值。

在RESP中，协议的不同部分都是使用一个 "\r\n" （`CRLF`）作为终止。

<a name="simple-string-reply"></a>

RESP简单字符串（RESP Simple Strings）
---------------------------------

简单字符串使用下面方法编码：一个 "+" 号，紧跟着一个不能包含CR或者LF的字符（不允许有新的一行），以`CRLF`终止（也就是："\r\n"）。

简单字符串用于以最小开销传输的非二进制安全字符串。例如很多的Redis命令在成功时仅仅使用"OK"作为回复，作为一个RESP简单字符串被使用一下5个字节编码：

    "+OK\r\n"


为了传输二进制安全字符串，RESP使用块字符串替代。

当Redis使用简单字符串回复时，client库应该使用第一个字符"+"之后的字符且不包括`CRLF`字节组成的字符串作为结果返回给调用者。

<a name="error-reply"></a>

RESP错误（RESP Errors）
---------------------

RESP有一个明确的错误数据类型。实际上，错误恰似RESP简单字符串，但是它的第一个字符是用"-"号替代"+"号。它们间真正的不同是：错误被客户端认为是异常，且
使用错误消息本身组成错误消息类型的字符串。

基本格式是：

    "-Error message\r\n"

错误回复仅仅在有错误发时生才会发送，例如：假设你试图对错的数据类型操作或者命令不存在等等。 当client库收到一个错误的回复，应当产生一个异常。

下面是错误回复的例子：

    -ERR unknown command 'foobar'
    -WRONGTYPE Operation against a key holding the wrong kind of value

"-"后的第一个单词，到遇到的第一个空格或者新的一行，表示返回的错误种类。这仅仅是一个使用Redis的约定，不是RESP错误格式的一部分。

例如， `ERR` 是通用错误，而 `WRONGTYPE` 是一个更加具体的错误，意味着客户端试图用错误的数据类型执行操作。这被称作一个 **错误前缀（Error Prefix
）** ， 是一个让客户端明白服务器返回的错误种类而不依赖于给出明确的信息，但是随着时间推移可能会改变。

一个客户端的实现可能针对不同的错误返回不同的异常类型，或者可以通过将错误名称作为字符串直接提供给调用者，从而提供捕获错误的通用方法吗。

但是，这种特性，不应该被认为是重要的，因为它很少使用，一些客户端的实现可能是简单的返回一个通用的错误条件，比如 `false`。

<a name="integer-reply"></a>

RESP 整型（RESP Integers）
------------------------

这个类型仅由前缀":"、中间字符串、`CRLF`结尾的字符串代表一个整数。例如：":0\r\n" 或者 ":1000\r\n" 返回的是整数。

许多Redis命令返回的是RESP整型，像 `INCR`、`LLEN` 和 `LASTSAVE`

这里返回整数没有什么特殊的意思，它仅仅就是`INCR`命令增加数字，`LASTSAVE`的一个UNIX时间等等。但是，返回的整数确保在一个有符号64位整数区间内。

整型返回也被广泛的应用到用在返回true或者false。比如命令`EXISTS` 或者 `SISMEMBER` 将会返回`1`表示true，`0`表示false。

其它命令比如： `SADD`、`SREM` 和 `SETNX`，如果命令实际被执行了，将会返回`1`，否则返回`0`。

以下命令将会返回整型： `SETNX`，`DEL`，`EXISTS`，`INCR`，`INCRBY`，`DECR`，`DECRBY`，`DBSIZE`，`LASTSAVE`，`RENAMENX`，`MOVE`，
`LLEN`，`SADD`，`SREM`，`SISMEMBER`，`SCARD`。

<a name="nil-reply"></a>
<a name="bulk-string-reply"></a>

RESP 块字符串（RESP Bulk Strings）
---------------------------------

块字符串被用来为了表示一个单一的二进制安全的512M长度的字符串

块字符串使用下面方式编码：

* 一个"$"字符紧跟着一个字节数组成的字符串（一个前缀长度），终止符`CRLF`。
* 实际的字符串数据。
* 最终的`CRLF`。

所以这个字符串 "foobar" 被编码成下面的形式：

    "$6\r\nfoobar\r\n"

一个空的字符串用下面形式表示：

    "$0\r\n\r\n"

RESP的块字符串也可以表示一个不存在的值得信号，使用一个特殊的格式经常用来表示一个`Null`值。这个特殊的格式，长度是-1，并且没有数据，所以Null表示如
下：

    "$-1\r\n"

这被叫做： **Null Bulk String**.

客户端库的API不应该返回一个空的字符串，应该是一个Nil对象，当服务器使用`Null Bulk String`回复时。例如，一个Ruby的库应当返回'Nil'而C的库应当返
回Null（或者设置一个标记在回复对象中）等等。

<a name="array-reply"></a>

RESP 数组（RESP Arrays）
----------------------

客户端使用RESP数组发送命令到Redis服务器。相似的确定的Redis命令使用RESP数组的回复类型返回元素集合到客户端。一个例子是`LRANGE`命令返回一个列表元
素。

RESP数组使用以下格式被发送：

* `*`是第一个字节，紧跟着是一个十进制数表示的数组中元素个数，然后是`CRLF`。
* 一个额外的RESP类型表示数组中所有元素类型

所以一个空的数组就像下面一样：

    "*0\r\n"

而数组中有两个`RESP Bulk Strings` "foo" 和 "bar" 被编码成：

    "*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"

正如你看到的，在数组的前面部分`*<count>CRLF`，另外的数据类型组成了数组的其它部分，一个接一个的连接。例如三个整型数组的编码如下：

    "*3\r\n:1\r\n:2\r\n:3\r\n"

数组可以包含混合类型，不必要求数组的所有元素都是相同的类型。例如：一个具有四个整型元素和一个块字符串的编码如下：

    *5\r\n
    :1\r\n
    :2\r\n
    :3\r\n
    :4\r\n
    $6\r\n
    foobar\r\n

（这个回复为了简单明了被分成了多行）。

服务器发送的第一行是`*5\r\n`是为了指明它接下来有五条回复。然后，每个回复多块`Multi Bulk`回复构成条目被传送。

空数组（Null Array）也是存在的，是一种另一种定义空值的方式（通常就是使用`Null Bulk String`，但是因为历史原因，我们有两种格式）。

例如，当`BLPOP`命令超时时，它将返回一个空数组，并且有一个`-1`数值，像下面一样：

    "*-1\r\n"

一个客户端库的API应当返回一个空（Null）对象，并不是一个空（empty）数组当Redis服务器使用一个空数组（Null Array）回复时。这是区分一个空列表和不同
条件产生的空所必须的（例如：超时的`BLPOP`命令）。

多维数组在RESP是必须额。例如一个数组有两个组成被编码成如下形式：

    *2\r\n
    *3\r\n
    :1\r\n
    :2\r\n
    :3\r\n
    *2\r\n
    +Foo\r\n
    -Bar\r\n

（这个回复为了简单明了被分成了多行）。

以上的RESP数据类型编码有两个元素数组，由一个数组包括1、2、3整数和一个数组包括一个简单字符、错误信息组成。

数组中的空元素（Null elements in Arrays）
-------------------------------------

一个数组的单一元素可能是Null。在Redis回复中经常被用到，为了标记那些元素丢失和非空字符串。这些可能发生在使用`SORT`命令时，当使用`GET` _pattern_
 选项时，当指定的key丢失时。下面的例子是一个数组包含一个Null元素：

    *3\r\n
    $3\r\n
    foo\r\n
    $-1\r\n
    $3\r\n
    bar\r\n

第二个元素是一个Null，客户端库可能是返回一些值，像这样：

    ["foo",nil,"bar"]

注意：这个不是我们前面章节中提出的特例，但是，刚刚的例子在进一步指定协议。

发送命令到Redis服务器（Sending commands to a Redis Server）
------------------------------------------------------

现在，你已经熟悉了RESP的序列化格式，编写一个Redis的客户端库的实现变得简单。我们可以进一步定义客户端与服务器是怎么交互工作的：

* 一个客户端发送到Redis服务器的RESP数组，仅由块字符串（Bulk Strings）组成。
* Redis服务器发送任一可用的RESP数据类型作为回复发送给客户端。

所以，一个典型的交互可能是一下形式：

客户端发送 **LLEN mylist** 命令，为了获取存放在key *mylist* 中的列表长度， 并且服务器回复一个整型回复，就像下面的例子：（C:客户端，S：服务器）

    C: *2\r\n
    C: $4\r\n
    C: LLEN\r\n
    C: $6\r\n
    C: mylist\r\n

    S: :48293\r\n

为了简洁明了，一般情况，我们会用新的一行分割协议的不同部分，但是实际交互中，客户端会发送`*2\r\n$4\r\nLLEN\r\n$6\r\nmylist\r\n`作为一个整体。

多条命令和管道（Multiple commands and pipelining）
----------------------------------------------

客户端可以使用同一个连接为了发送多条命令。由于支持Pipelining命令，所以，客户端可以通过一次写操作发送多条命令。不需要读取服务器回复上一条命令后再发送
下一条命令。在最后所有的服务都会被读取。

获取更多信息，请查看 [关于Pipelining](/topics/pipelining)。

内联命令（Inline Commands）
------------------------

有时，你只能使用`telnet`，并且你需要发送一个命令到Redis服务器。虽然Redis协议实现简单，但它也不是理想的方式用在交互会话中，且`redis-cli`也许不可
用。针对这个原因，Redis专门为人们设计了一个特殊的方式接收命令，被叫做 **内联命令（inline command）** 格式。

下面是一个服务器/客户端聊天使用的内联命令形式（服务器以S:开始，客户端以C:开始）：
The following is an example of a server/client chat using an inline command
(the server chat starts with S:, the client chat with C:)

    C: PING
    S: +PONG

下面是另外一个例子，返回一个整数：

    C: EXISTS somekey
    S: :0
在telnet会话中，基本上只要你简单的写一个空格分割参数。由于没命令是以`*`开始的，使用统一请求协议替代。Redis能够明白这个条件，并解析你的命令。

高性能的Redis协议解析器（High performance parser for the Redis protocol）
----------------------------------------------

由于这个Redis协议非常的人类可读且易于实现，可以用类似于二进制协议的性能来实现。

RESP使用前缀长度实现块数据传递，所以，这里永远不需要浏览消息体的特殊字符，例如json的实现，也不需要引用这个消息体发送到服务器。

块和多块长度可以用代码执行，每个代码执行单个操作，同时扫描CR字符，如下面的C代码


```
#include <stdio.h>

int main(void) {
    unsigned char *p = "$123\r\n";
    int len = 0;

    p++;
    while(*p != '\r') {
        len = (len*10)+(*p - '0');
        p++;
    }

    /* Now p points at '\r', and the len is in bulk_len. */
    printf("%d\n", len);
    return 0;
}
```

在第一个CR标识后，就可以跳过紧跟在其后的LF字符不用做任何处理。然后就可以读取块数据，并不需要检查消息载体在任何方式中。最终，剩下的CR和LF被丢弃不做任
何处理。

跟二进制协议性能对比，Redis协议在实现起来明显是简单的在很多高级语言中，减少客户端软件的bug数量。